import { randomUUID } from "node:crypto";
import { mkdir, open, unlink } from "node:fs/promises";
import { resolve } from "node:path";
import { env } from "@/env";
import { applyUpdate, request } from "./management";

const DEFAULT_REPOSITORY = "GameTec-live/atlas";
const MAX_UPDATE_BYTES = 4 * 1024 ** 3;
const updateAssetPattern = /^atlas-rpi5-.+-update\.tar\.zst$/;
let updateReserved = false;

function fail(status: number, code: string, message: string): never {
    throw Response.json({ error: { code, message } }, { status });
}

const applyStream = async (
    getStream: () => ReadableStream<Uint8Array> | null,
    declaredLength?: number,
) => {
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
        if (!management.ok) return management;
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
        updateReserved = false;
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
