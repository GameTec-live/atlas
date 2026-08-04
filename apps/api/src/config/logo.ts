import { randomUUID } from "node:crypto";
import { mkdir, open, readdir, rename, stat, unlink } from "node:fs/promises";
import { resolve } from "node:path";

export const MAX_LOGO_SIZE = 5 * 1024 * 1024;

const LOGO_LOCK_RETRY_INTERVAL = 25;
const LOGO_LOCK_TIMEOUT = 10_000;

export const logoExtensions = {
    "image/avif": "avif",
    "image/bmp": "bmp",
    "image/gif": "gif",
    "image/jpeg": "jpg",
    "image/png": "png",
    "image/svg+xml": "svg",
    "image/vnd.microsoft.icon": "ico",
    "image/webp": "webp",
    "image/x-icon": "ico",
} as const;

export type LogoContentType = keyof typeof logoExtensions;

export interface LogoStorage {
    readonly dataLocation: string;
    readonly logoName: string;
}

export class LogoTooLargeError extends Error {
    constructor() {
        super(`Logo exceeds the ${MAX_LOGO_SIZE}-byte size limit`);
        this.name = "LogoTooLargeError";
    }
}

export class LogoLockTimeoutError extends Error {
    constructor() {
        super("Timed out waiting to replace the logo");
        this.name = "LogoLockTimeoutError";
    }
}

export const isLogoContentType = (
    contentType: string,
): contentType is LogoContentType => Object.hasOwn(logoExtensions, contentType);

export const isFileSystemError = (
    error: unknown,
    code: string,
): error is NodeJS.ErrnoException =>
    error instanceof Error && "code" in error && error.code === code;

const supportedLogoFileNames = ({ logoName }: LogoStorage) =>
    new Set(
        Object.values(logoExtensions).map(
            (extension) => `${logoName}.${extension}`,
        ),
    );

export const listLogoFiles = async ({
    dataLocation,
    logoName,
}: LogoStorage) => {
    try {
        const entries = await readdir(dataLocation, {
            withFileTypes: true,
        });
        const supportedFileNames = supportedLogoFileNames({
            dataLocation,
            logoName,
        });

        return entries
            .filter(
                (entry) => entry.isFile() && supportedFileNames.has(entry.name),
            )
            .map((entry) => resolve(dataLocation, entry.name));
    } catch (error) {
        if (isFileSystemError(error, "ENOENT")) return [];
        throw error;
    }
};

export const findLogoFile = async (storage: LogoStorage) => {
    const candidates = await Promise.all(
        (await listLogoFiles(storage)).map(async (path) => {
            try {
                return { path, modifiedAt: (await stat(path)).mtimeMs };
            } catch (error) {
                if (isFileSystemError(error, "ENOENT")) return undefined;
                throw error;
            }
        }),
    );

    return candidates
        .filter((candidate) => candidate !== undefined)
        .sort(
            (left, right) =>
                right.modifiedAt - left.modifiedAt ||
                left.path.localeCompare(right.path),
        )[0]?.path;
};

export const removeOtherLogoFiles = async (
    storage: LogoStorage,
    logoFilePath: string,
) => {
    await Promise.all(
        (await listLogoFiles(storage))
            .filter((path) => path !== logoFilePath)
            .map(async (path) => {
                try {
                    await unlink(path);
                } catch (error) {
                    if (!isFileSystemError(error, "ENOENT")) throw error;
                }
            }),
    );
};

const wait = (duration: number) =>
    new Promise((resolveWait) => setTimeout(resolveWait, duration));

const acquireLogoLock = async ({ dataLocation, logoName }: LogoStorage) => {
    const lockPath = resolve(dataLocation, `.${logoName}.lock`);
    const deadline = Date.now() + LOGO_LOCK_TIMEOUT;

    while (true) {
        try {
            const lock = await open(lockPath, "wx");

            return async () => {
                await lock.close();
                await unlink(lockPath);
            };
        } catch (error) {
            if (!isFileSystemError(error, "EEXIST")) throw error;
            if (Date.now() >= deadline) throw new LogoLockTimeoutError();
            await wait(LOGO_LOCK_RETRY_INTERVAL);
        }
    }
};

const unlinkIfExists = async (path: string) => {
    try {
        await unlink(path);
    } catch (error) {
        if (!isFileSystemError(error, "ENOENT")) throw error;
    }
};

export const readLogoBody = async (
    request: Request,
    contentType: LogoContentType,
) => {
    const declaredLength = Number(request.headers.get("content-length"));
    if (Number.isFinite(declaredLength) && declaredLength > MAX_LOGO_SIZE) {
        throw new LogoTooLargeError();
    }

    const reader = request.body?.getReader();
    if (!reader) return new Blob([], { type: contentType });

    const chunks: Uint8Array[] = [];
    let size = 0;

    while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        size += value.byteLength;
        if (size > MAX_LOGO_SIZE) {
            await reader.cancel();
            throw new LogoTooLargeError();
        }
        chunks.push(value);
    }

    return new Blob(chunks, { type: contentType });
};

export const replaceLogo = async (
    storage: LogoStorage,
    extension: (typeof logoExtensions)[LogoContentType],
    logo: Blob,
) => {
    await mkdir(storage.dataLocation, { recursive: true });
    const releaseLock = await acquireLogoLock(storage);
    const logoFilePath = resolve(
        storage.dataLocation,
        `${storage.logoName}.${extension}`,
    );
    const temporaryFilePath = resolve(
        storage.dataLocation,
        `.${storage.logoName}.${randomUUID()}.tmp`,
    );

    try {
        await Bun.write(temporaryFilePath, logo);
        await rename(temporaryFilePath, logoFilePath);
        await removeOtherLogoFiles(storage, logoFilePath);
    } finally {
        try {
            await unlinkIfExists(temporaryFilePath);
        } finally {
            await releaseLock();
        }
    }
};
