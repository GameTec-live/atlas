import { localDateKey } from "@/lib/date";

type DistanceEntry = {
    invalid: boolean;
    startOdometer: number;
    endOdometer: number | null;
    startedAt: Date;
};

export function getDailyDistanceData(
    entries: readonly DistanceEntry[],
    locale: string,
    numberOfDays = 5,
    now = new Date(),
) {
    const dates = Array.from({ length: numberOfDays }, (_, index) => {
        const date = new Date(now);
        date.setHours(0, 0, 0, 0);
        date.setDate(date.getDate() - (numberOfDays - 1 - index));
        return date;
    });
    const kilometersByDate = new Map(
        dates.map((date) => [localDateKey(date), 0]),
    );

    for (const entry of entries) {
        if (entry.invalid || entry.endOdometer === null) continue;

        const key = localDateKey(entry.startedAt);
        const current = kilometersByDate.get(key);
        if (current === undefined) continue;

        kilometersByDate.set(
            key,
            current + Math.max(0, entry.endOdometer - entry.startOdometer),
        );
    }

    return dates.map((date) => ({
        date: localDateKey(date),
        day: new Intl.DateTimeFormat(locale, {
            weekday: "short",
            day: "numeric",
        }).format(date),
        kilometers: kilometersByDate.get(localDateKey(date)) ?? 0,
    }));
}
