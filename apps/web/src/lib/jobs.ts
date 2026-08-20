import type { Coordinates } from "@/lib/route";

export function getJobAddresses(job: object) {
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
    coordinates: Coordinates | null,
    fallback: string,
) {
    if (address) return address;
    if (!coordinates) return fallback;
    return coordinates.map((coordinate) => coordinate.toFixed(5)).join(", ");
}
