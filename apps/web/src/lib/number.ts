/** Formats a number for the active locale with a configurable null fallback. */
export function formatNumber(
    value: number | null,
    locale: string,
    fallback = "",
) {
    return value === null
        ? fallback
        : new Intl.NumberFormat(locale).format(value);
}

export function parseOptionalNumber(value: string) {
    return value.trim() === "" ? undefined : Number(value);
}

export function formatBytes(bytes: number, locale: string) {
    if (bytes === 0) return "0 B";

    const units = ["B", "KB", "MB", "GB", "TB"] as const;
    const unit = Math.min(
        Math.floor(Math.log(bytes) / Math.log(1024)),
        units.length - 1,
    );
    const value = bytes / 1024 ** unit;

    return `${new Intl.NumberFormat(locale, {
        maximumFractionDigits: 1,
    }).format(value)} ${units[unit]}`;
}
