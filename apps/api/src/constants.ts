declare const BUILD_VERSION: string;
declare const BUILD_TIME: string;
declare const GIT_COMMIT: string;

export const BUILD_INFO = {
    version:
        typeof BUILD_VERSION === "undefined" ? "development" : BUILD_VERSION,
    time: typeof BUILD_TIME === "undefined" ? "unknown" : BUILD_TIME,
    commit: typeof GIT_COMMIT === "undefined" ? "unknown" : GIT_COMMIT,
};
