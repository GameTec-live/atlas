import { readdir, stat, unlink } from "node:fs/promises";
import { resolve } from "node:path";

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

export const isLogoContentType = (
    contentType: string,
): contentType is LogoContentType => Object.hasOwn(logoExtensions, contentType);

export const isFileSystemError = (
    error: unknown,
    code: string,
): error is NodeJS.ErrnoException =>
    error instanceof Error && "code" in error && error.code === code;

export const listLogoFiles = async ({
    dataLocation,
    logoName,
}: LogoStorage) => {
    try {
        const entries = await readdir(dataLocation, {
            withFileTypes: true,
        });
        const logoPrefix = `${logoName}.`;

        return entries
            .filter(
                (entry) => entry.isFile() && entry.name.startsWith(logoPrefix),
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
