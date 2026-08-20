/** Formats a date in the browser's local timezone without a time. */
export function formatDate(value: Date | null, locale: string, fallback = "") {
    if (!value) return fallback;

    return new Intl.DateTimeFormat(locale, { dateStyle: "medium" }).format(
        value,
    );
}

/** Formats a server timestamp in the browser's local timezone. */
export function formatDateTime(
    value: Date | null,
    locale: string,
    fallback = "",
) {
    if (!value) return fallback;

    return new Intl.DateTimeFormat(locale, {
        dateStyle: "medium",
        timeStyle: "short",
    }).format(value);
}

/** Returns a stable key for the date containing a timestamp in local time. */
export function localDateKey(value: Date) {
    return `${value.getFullYear()}-${value.getMonth()}-${value.getDate()}`;
}

export function formatLongDate(value: Date, locale: string) {
    return new Intl.DateTimeFormat(locale, { dateStyle: "long" }).format(value);
}

export function formatLongDateOrToday(
    value: Date,
    locale: string,
    todayLabel: string,
    now = new Date(),
) {
    return localDateKey(value) === localDateKey(now)
        ? todayLabel
        : formatLongDate(value, locale);
}
