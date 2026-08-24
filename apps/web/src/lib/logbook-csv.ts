import { downloadCsv } from "@/lib/csv";
import { formatDate, formatTime } from "@/lib/date";
import { formatNumber } from "@/lib/number";
import type { LogbookEntry } from "@/queries/logbooks";

export type LogbookCsvLabels = {
    date: string;
    licensePlate: string;
    vehicle: string;
    kmStart: string;
    kmEnd: string;
    timeStart: string;
    timeEnd: string;
    revenue: string;
    status: string;
    valid: string;
    invalid: string;
    notAvailable: string;
};

export function formatLogbookVehicle(entry: LogbookEntry, fallback: string) {
    if (!entry.vehicle) return fallback;
    return [entry.vehicle.brand, entry.vehicle.model].filter(Boolean).join(" ");
}

export function downloadLogbookCsv({
    entries,
    filename,
    locale,
    labels,
}: {
    entries: LogbookEntry[];
    filename: string;
    locale: string;
    labels: LogbookCsvLabels;
}) {
    downloadCsv(filename, [
        [
            labels.date,
            labels.licensePlate,
            labels.vehicle,
            labels.kmStart,
            labels.kmEnd,
            labels.timeStart,
            labels.timeEnd,
            labels.revenue,
            labels.status,
        ],
        ...entries.map((entry) => [
            formatDate(entry.startedAt, locale),
            entry.vehicle?.licensePlate ?? labels.notAvailable,
            formatLogbookVehicle(entry, labels.notAvailable),
            formatNumber(entry.startOdometer, locale),
            formatNumber(entry.endOdometer, locale),
            formatTime(entry.startedAt, locale),
            entry.endedAt
                ? formatTime(entry.endedAt, locale)
                : labels.notAvailable,
            entry.revenue === null
                ? labels.notAvailable
                : new Intl.NumberFormat(locale, {
                      style: "currency",
                      currency: "EUR",
                  }).format(entry.revenue),
            entry.invalid ? labels.invalid : labels.valid,
        ]),
    ]);
}
