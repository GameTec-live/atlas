import { randomUUID } from "node:crypto";
import { mkdir, open, unlink } from "node:fs/promises";
import { resolve } from "node:path";
import { env } from "@/env";
import { applyUpdate, request } from "./management";

const DEFAULT_REPOSITORY = "GameTec-live/atlas";
const MAX_UPDATE_BYTES = 4 * 1024 ** 3;
const UPLOAD_CHUNK_BYTES = 8 * 1024 ** 2;
const UPLOAD_IDLE_TIMEOUT_MS = 15 * 60 * 1000;
const updateAssetPattern = /^atlas-rpi5-.+-update\.tar\.zst$/;
let updateReserved = false;

interface StagedUpload {
    id: string;
    path: string;
    size: number;
    received: number;
    writing: boolean;
    expiration?: ReturnType<typeof setTimeout>;
}

let stagedUpload: StagedUpload | undefined;

function fail(status: number, code: string, message: string): never {
    throw Response.json({ error: { code, message } }, { status });
}

const releaseUpdate = () => {
    updateReserved = false;
};

const reserveUpdate = async (): Promise<Response | undefined> => {
    if (updateReserved) {
        fail(
            409,
            "operation_in_progress",
            "Another firmware update is already being prepared",
        );
    }
    updateReserved = true;

    try {
        const management = await request("/api/v1/update");
        if (!management.ok) {
            releaseUpdate();
            return management;
        }

        const updateStatus = (await management.json()) as {
            update?: { pending?: string };
        };
        if (updateStatus.update?.pending) {
            fail(
                409,
                "update_pending",
                "An Atlas OS update is already pending",
            );
        }
        return undefined;
    } catch (error) {
        releaseUpdate();
        throw error;
    }
};

const discardUpload = async (upload: StagedUpload) => {
    if (stagedUpload?.id === upload.id) stagedUpload = undefined;
    if (upload.expiration) clearTimeout(upload.expiration);
    releaseUpdate();
    await unlink(upload.path).catch(() => undefined);
};

const refreshUploadExpiration = (upload: StagedUpload) => {
    if (upload.expiration) clearTimeout(upload.expiration);
    upload.expiration = setTimeout(() => {
        if (upload.writing) {
            refreshUploadExpiration(upload);
            return;
        }
        void discardUpload(upload);
    }, UPLOAD_IDLE_TIMEOUT_MS);
};

const activeUpload = (id: string) => {
    const upload = stagedUpload;
    if (!upload || upload.id !== id) {
        fail(404, "upload_not_found", "Firmware upload was not found");
    }
    return upload;
};

const applyStream = async (
    getStream: () => ReadableStream<Uint8Array> | null,
    declaredLength?: number,
) => {
    const management = await reserveUpdate();
    if (management) return management;

    try {
        if (declaredLength !== undefined && declaredLength > MAX_UPDATE_BYTES) {
            fail(
                413,
                "update_too_large",
                `Update file exceeds the ${MAX_UPDATE_BYTES}-byte size limit`,
            );
        }
        const stream = getStream();
        if (!stream) fail(400, "empty_update", "Update file is empty");

        const stagingDirectory = resolve(
            env.DATA_STORAGE_PATH ?? "./data",
            "system-updates",
        );
        await mkdir(stagingDirectory, { recursive: true });
        const path = resolve(
            stagingDirectory,
            `update-${randomUUID()}.tar.zst`,
        );
        const file = await open(path, "wx", 0o600);
        const reader = stream.getReader();
        let size = 0;

        try {
            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                size += value.byteLength;
                if (size > MAX_UPDATE_BYTES) {
                    await reader.cancel();
                    fail(
                        413,
                        "update_too_large",
                        `Update file exceeds the ${MAX_UPDATE_BYTES}-byte size limit`,
                    );
                }
                await file.writeFile(value);
            }

            if (size === 0) fail(400, "empty_update", "Update file is empty");
            await file.sync();
            await file.close();
            return await applyUpdate(path);
        } finally {
            await file.close().catch(() => undefined);
            await unlink(path).catch(() => undefined);
        }
    } finally {
        releaseUpdate();
    }
};

export const fromUpload = async (request: Request) => {
    const contentLength = request.headers.get("content-length");
    const declaredLength = Number(contentLength);
    return applyStream(
        () => request.body,
        contentLength !== null &&
            Number.isSafeInteger(declaredLength) &&
            declaredLength >= 0
            ? declaredLength
            : undefined,
    );
};

export const createUpload = async (size: number) => {
    if (!Number.isSafeInteger(size) || size <= 0) {
        fail(400, "invalid_update_size", "Update file size is invalid");
    }
    if (size > MAX_UPDATE_BYTES) {
        fail(
            413,
            "update_too_large",
            `Update file exceeds the ${MAX_UPDATE_BYTES}-byte size limit`,
        );
    }

    const management = await reserveUpdate();
    if (management) return management;

    let path: string | undefined;
    try {
        const stagingDirectory = resolve(
            env.DATA_STORAGE_PATH ?? "./data",
            "system-updates",
        );
        await mkdir(stagingDirectory, { recursive: true });
        const id = randomUUID();
        path = resolve(stagingDirectory, `update-${id}.tar.zst`);
        const file = await open(path, "wx", 0o600);
        await file.close();

        const upload: StagedUpload = {
            id,
            path,
            size,
            received: 0,
            writing: false,
        };
        stagedUpload = upload;
        refreshUploadExpiration(upload);

        return Response.json(
            { uploadId: id, chunkSize: UPLOAD_CHUNK_BYTES },
            { status: 201 },
        );
    } catch (error) {
        releaseUpdate();
        if (path) await unlink(path).catch(() => undefined);
        throw error;
    }
};

const parseContentRange = (value: string | null) => {
    const match = /^bytes (\d+)-(\d+)\/(\d+)$/.exec(value ?? "");
    if (!match) {
        fail(
            400,
            "invalid_content_range",
            "A valid Content-Range header is required",
        );
    }
    const [, startValue, endValue, totalValue] = match;
    const start = Number(startValue);
    const end = Number(endValue);
    const total = Number(totalValue);
    if (
        !Number.isSafeInteger(start) ||
        !Number.isSafeInteger(end) ||
        !Number.isSafeInteger(total)
    ) {
        fail(400, "invalid_content_range", "Content-Range is invalid");
    }
    return { start, end, total };
};

export const appendUpload = async (id: string, request: Request) => {
    const upload = activeUpload(id);
    if (upload.writing) {
        fail(409, "upload_busy", "A firmware chunk is already being written");
    }

    const { start, end, total } = parseContentRange(
        request.headers.get("content-range"),
    );
    const chunkSize = end - start + 1;
    if (
        total !== upload.size ||
        start !== upload.received ||
        end < start ||
        end >= total ||
        chunkSize > UPLOAD_CHUNK_BYTES
    ) {
        fail(
            409,
            "unexpected_upload_range",
            `Expected the next chunk to start at byte ${upload.received}`,
        );
    }

    const contentLengthHeader = request.headers.get("content-length");
    const contentLength = Number(contentLengthHeader);
    if (
        contentLengthHeader !== null &&
        Number.isSafeInteger(contentLength) &&
        contentLength !== chunkSize
    ) {
        fail(
            400,
            "invalid_chunk_size",
            "Chunk length does not match Content-Range",
        );
    }

    const stream = request.body;
    if (!stream) fail(400, "empty_update_chunk", "Update chunk is empty");

    upload.writing = true;
    refreshUploadExpiration(upload);
    const reader = stream.getReader();
    let written = 0;
    let file: Awaited<ReturnType<typeof open>> | undefined;

    try {
        file = await open(upload.path, "r+");
        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            const valueStart = written;
            written += value.byteLength;
            if (written > chunkSize) {
                await reader.cancel();
                fail(
                    400,
                    "invalid_chunk_size",
                    "Chunk length exceeds Content-Range",
                );
            }
            let valueOffset = 0;
            while (valueOffset < value.byteLength) {
                const { bytesWritten } = await file.write(
                    value,
                    valueOffset,
                    value.byteLength - valueOffset,
                    start + valueStart + valueOffset,
                );
                if (bytesWritten === 0) {
                    throw new Error("Could not write firmware upload chunk");
                }
                valueOffset += bytesWritten;
            }
        }
        if (written !== chunkSize) {
            fail(
                400,
                "invalid_chunk_size",
                "Chunk length does not match Content-Range",
            );
        }

        await file.sync();
        upload.received += written;
        return Response.json({ received: upload.received });
    } finally {
        await file?.close().catch(() => undefined);
        upload.writing = false;
        refreshUploadExpiration(upload);
    }
};

export const installUpload = (id: string) => {
    const upload = activeUpload(id);
    if (upload.writing) {
        fail(409, "upload_busy", "A firmware chunk is still being written");
    }
    if (upload.received !== upload.size) {
        fail(
            409,
            "upload_incomplete",
            `Firmware upload is incomplete (${upload.received}/${upload.size} bytes)`,
        );
    }

    stagedUpload = undefined;
    if (upload.expiration) clearTimeout(upload.expiration);
    void (async () => {
        try {
            const response = await applyUpdate(upload.path);
            if (!response.ok) {
                console.error(
                    "Failed to apply uploaded firmware update",
                    response.status,
                    await response.text(),
                );
            }
        } catch (error) {
            console.error("Failed to apply uploaded firmware update", error);
        } finally {
            await unlink(upload.path).catch(() => undefined);
            releaseUpdate();
        }
    })();

    return Response.json({ status: "installing" }, { status: 202 });
};

export const cancelUpload = async (id: string) => {
    const upload = activeUpload(id);
    if (upload.writing) {
        fail(409, "upload_busy", "A firmware chunk is still being written");
    }
    await discardUpload(upload);
    return Response.json({ status: "ok" });
};

export const fromURL = async (value: string) => {
    let url: URL;
    try {
        url = new URL(value);
    } catch {
        fail(400, "invalid_update_url", "Update URL is invalid");
    }
    if (url.protocol !== "https:" && url.protocol !== "http:") {
        fail(400, "invalid_update_url", "Update URL must use HTTP or HTTPS");
    }

    let response: Response;
    try {
        response = await fetch(url, {
            headers: { "user-agent": "Atlas-API" },
            redirect: "follow",
        });
    } catch {
        fail(
            502,
            "update_download_failed",
            "Could not download the update file",
        );
    }
    if (!response.ok || !response.body) {
        fail(
            502,
            "update_download_failed",
            `Update download returned HTTP ${response.status}`,
        );
    }

    const length = Number(response.headers.get("content-length"));
    const stream = response.body;
    return applyStream(
        () => stream,
        Number.isSafeInteger(length) && length >= 0 ? length : undefined,
    );
};

export const fromLatestGitHub = async (repository = DEFAULT_REPOSITORY) => {
    let response: Response;
    try {
        response = await fetch(
            `https://api.github.com/repos/${repository}/releases/latest`,
            {
                headers: {
                    accept: "application/vnd.github+json",
                    "user-agent": "Atlas-API",
                    "x-github-api-version": "2022-11-28",
                },
            },
        );
    } catch {
        fail(
            502,
            "github_release_failed",
            "Could not query the latest GitHub release",
        );
    }
    if (!response.ok) {
        fail(
            502,
            "github_release_failed",
            `GitHub release lookup returned HTTP ${response.status}`,
        );
    }

    const release = (await response
        .json()
        .catch(() =>
            fail(
                502,
                "github_release_failed",
                "GitHub returned an invalid release response",
            ),
        )) as {
        assets?: Array<{ name?: string; browser_download_url?: string }>;
    };
    const asset = release.assets?.find(
        (candidate) =>
            typeof candidate.name === "string" &&
            updateAssetPattern.test(candidate.name) &&
            typeof candidate.browser_download_url === "string",
    );
    if (!asset?.browser_download_url) {
        fail(
            502,
            "github_update_missing",
            "The latest GitHub release has no Atlas OS update asset",
        );
    }

    return fromURL(asset.browser_download_url);
};
