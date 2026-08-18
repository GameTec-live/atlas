import type { Job } from "@/queries/jobs";

export function getJobAddresses(job: Job) {
    const from =
        "fromAddress" in job && typeof job.fromAddress === "string"
            ? job.fromAddress
            : undefined;
    const to =
        "toAddress" in job &&
        (typeof job.toAddress === "string" || job.toAddress === null)
            ? job.toAddress
            : undefined;
    return { from, to };
}

export function formatJobLocation(
    address: string | null | undefined,
    coordinates: [number, number] | null,
    fallback: string,
) {
    if (address) return address;
    if (!coordinates) return fallback;
    return coordinates.map((coordinate) => coordinate.toFixed(5)).join(", ");
}
