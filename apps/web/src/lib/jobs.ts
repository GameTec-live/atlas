import type { Coordinates } from "@/lib/route";

type AssignableJob = {
    assignedDriverId: string | null;
    dueDate: Date;
};

type ScheduledJob = AssignableJob & {
    completedAt: Date | null;
    startedAt: Date | null;
};

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

export function getCurrentJob<T extends ScheduledJob>(
    jobs: readonly T[],
    driverId: string,
) {
    return jobs
        .filter(
            (job) =>
                job.assignedDriverId === driverId && job.completedAt === null,
        )
        .sort((left, right) => {
            const startedDifference =
                Number(right.startedAt !== null) -
                Number(left.startedAt !== null);
            return (
                startedDifference ||
                left.dueDate.getTime() - right.dueDate.getTime()
            );
        })[0];
}

export function getAssignedJobs<T extends AssignableJob>(jobs: readonly T[]) {
    return jobs
        .filter((job) => job.assignedDriverId !== null)
        .sort(
            (left, right) => right.dueDate.getTime() - left.dueDate.getTime(),
        );
}

export function getUnassignedJobs<T extends AssignableJob>(jobs: readonly T[]) {
    return jobs
        .filter((job) => job.assignedDriverId === null)
        .sort(
            (left, right) => left.dueDate.getTime() - right.dueDate.getTime(),
        );
}
