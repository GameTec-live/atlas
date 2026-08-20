import { downloadCsv } from "@/lib/csv";
import { formatDateTime } from "@/lib/date";
import { formatJobLocation, getJobAddresses } from "@/lib/jobs";
import type { Job } from "@/queries/jobs";

export type JobCsvLabels = {
    from: string;
    to: string;
    due: string;
    started: string;
    completed: string;
    assigned: string;
    assignedDriverId: string;
    vehicleId: string;
    note: string;
    created: string;
    notAvailable: string;
};

export function downloadJobsCsv({
    jobs,
    filename,
    locale,
    labels,
}: {
    jobs: Job[];
    filename: string;
    locale: string;
    labels: JobCsvLabels;
}) {
    const rows = [
        [
            "ID",
            labels.from,
            labels.to,
            labels.due,
            labels.started,
            labels.completed,
            labels.assigned,
            labels.assignedDriverId,
            labels.vehicleId,
            labels.note,
            labels.created,
        ],
        ...jobs.map((job) => {
            const addresses = getJobAddresses(job);
            return [
                job.id,
                formatJobLocation(
                    addresses.from,
                    job.from,
                    labels.notAvailable,
                ),
                formatJobLocation(addresses.to, job.to, labels.notAvailable),
                formatDateTime(job.dueDate, locale),
                formatDateTime(job.startedAt, locale),
                formatDateTime(job.completedAt, locale),
                job.assignedDriverName ?? "",
                job.assignedDriverId ?? "",
                job.vehicleId ?? "",
                job.note ?? "",
                formatDateTime(job.createdAt, locale),
            ];
        }),
    ];

    downloadCsv(filename, rows);
}
