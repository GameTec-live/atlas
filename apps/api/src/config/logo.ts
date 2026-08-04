import { randomUUID } from "node:crypto";
import {
    access,
    mkdir,
    readdir,
    rename,
    rm,
    rmdir,
    stat,
    unlink,
    utimes,
    writeFile,
} from "node:fs/promises";
import { extname, resolve } from "node:path";

export const MAX_LOGO_SIZE = 5 * 1024 * 1024;

const LOGO_LOCK_RETRY_INTERVAL = 25;
const LOGO_LOCK_TIMEOUT = 10_000;
const LOGO_LOCK_LEASE_DURATION = 5_000;
const LOGO_LOCK_HEARTBEAT_INTERVAL = 1_000;

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

export class LogoLockError extends Error {
    constructor(message: string) {
        super(message);
        this.name = "LogoLockError";
    }
}

class LogoLockTimeoutError extends LogoLockError {
    constructor() {
        super("Timed out waiting to replace the logo");
    }
}

class LogoLockLostError extends LogoLockError {
    constructor() {
        super("Lost ownership of the logo replacement lock");
    }
}

export const isLogoContentType = (
    contentType: string,
): contentType is LogoContentType => Object.hasOwn(logoExtensions, contentType);

export const getLogoContentType = (path: string) => {
    const extension = extname(path).slice(1).toLowerCase();

    return (
        Object.entries(logoExtensions).find(
            ([, supportedExtension]) => supportedExtension === extension,
        )?.[0] ?? "application/octet-stream"
    );
};

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

const getLogoLockModifiedAt = async (lockPath: string) => {
    const lockStats = await stat(lockPath);
    if (!lockStats.isDirectory()) return lockStats.mtimeMs;

    const entries = await readdir(lockPath, { withFileTypes: true });
    const ownerTokens = entries.filter(
        (entry) => entry.isFile() && entry.name.startsWith("owner-"),
    );

    if (ownerTokens.length === 0) return lockStats.mtimeMs;

    return Math.max(
        ...(await Promise.all(
            ownerTokens.map(
                async (entry) =>
                    (
                        await stat(resolve(lockPath, entry.name))
                    ).mtimeMs,
            ),
        )),
    );
};

const recoverStaleLogoLock = async (lockPath: string) => {
    let modifiedAt: number;
    try {
        modifiedAt = await getLogoLockModifiedAt(lockPath);
    } catch (error) {
        if (isFileSystemError(error, "ENOENT")) return true;
        throw error;
    }

    if (Date.now() - modifiedAt <= LOGO_LOCK_LEASE_DURATION) return false;

    const abandonedLockPath = `${lockPath}.abandoned-${randomUUID()}`;
    try {
        await rename(lockPath, abandonedLockPath);
    } catch (error) {
        if (isFileSystemError(error, "ENOENT")) return true;
        throw error;
    }

    await rm(abandonedLockPath, { recursive: true, force: true });
    return true;
};

const acquireLogoLock = async ({ dataLocation, logoName }: LogoStorage) => {
    const lockPath = resolve(dataLocation, `.${logoName}.lock`);
    const deadline = Date.now() + LOGO_LOCK_TIMEOUT;

    while (true) {
        const ownerTokenPath = resolve(lockPath, `owner-${randomUUID()}`);

        try {
            await mkdir(lockPath);
            try {
                await writeFile(ownerTokenPath, "", { flag: "wx" });
            } catch (error) {
                await rmdir(lockPath);
                throw error;
            }
        } catch (error) {
            if (!isFileSystemError(error, "EEXIST")) throw error;
            if (await recoverStaleLogoLock(lockPath)) continue;
            if (Date.now() >= deadline) throw new LogoLockTimeoutError();
            await wait(LOGO_LOCK_RETRY_INTERVAL);
            continue;
        }

        let heartbeatError: unknown;
        let heartbeatUpdate = Promise.resolve();
        const refreshLease = () => {
            heartbeatUpdate = heartbeatUpdate.then(async () => {
                if (heartbeatError) return;
                try {
                    const now = new Date();
                    await utimes(ownerTokenPath, now, now);
                } catch (error) {
                    heartbeatError = error;
                }
            });
        };
        const heartbeat = setInterval(
            refreshLease,
            LOGO_LOCK_HEARTBEAT_INTERVAL,
        );
        heartbeat.unref();

        const assertOwned = async () => {
            await heartbeatUpdate;
            if (heartbeatError) throw new LogoLockLostError();

            try {
                await access(ownerTokenPath);
            } catch {
                throw new LogoLockLostError();
            }
        };

        const release = async () => {
            clearInterval(heartbeat);
            await heartbeatUpdate;

            try {
                await unlink(ownerTokenPath);
            } catch (error) {
                if (isFileSystemError(error, "ENOENT")) return;
                throw error;
            }

            try {
                await rmdir(lockPath);
            } catch (error) {
                if (!isFileSystemError(error, "ENOENT")) throw error;
            }
        };

        return { assertOwned, release };
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
    const lock = await acquireLogoLock(storage);
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
        await lock.assertOwned();
        await rename(temporaryFilePath, logoFilePath);
        await lock.assertOwned();
        await removeOtherLogoFiles(storage, logoFilePath);
    } finally {
        try {
            await unlinkIfExists(temporaryFilePath);
        } finally {
            await lock.release();
        }
    }
};
