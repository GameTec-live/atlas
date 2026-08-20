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
