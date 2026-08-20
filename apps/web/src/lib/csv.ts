export type CsvValue = string | number | boolean | null | undefined;

function escapeCsvCell(value: CsvValue) {
    const raw = value === null || value === undefined ? "" : String(value);
    const safe = /^[=+\-@]/.test(raw) ? `'${raw}` : raw;
    return `"${safe.replaceAll('"', '""')}"`;
}

export function serializeCsv(rows: CsvValue[][]) {
    return rows.map((row) => row.map(escapeCsvCell).join(",")).join("\r\n");
}

export function downloadCsv(filename: string, rows: CsvValue[][]) {
    const url = URL.createObjectURL(
        new Blob(["\uFEFF", serializeCsv(rows)], {
            type: "text/csv;charset=utf-8",
        }),
    );
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
}
