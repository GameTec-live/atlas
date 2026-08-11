import type { job } from "../db/schema";
import { reverseGeocode } from "../geoservices/geocoder";
import { notify } from "../realtime";

export const NOTIFICATION_ADDRESS_MAX_LENGTH = 80;

export const shortenAddress = (address: string, maxLength: number) => {
    const characters = Array.from(address);
    if (characters.length <= maxLength) return address;
    if (maxLength <= 0) return "";
    if (maxLength === 1) return "…";

    return `${characters
        .slice(0, maxLength - 1)
        .join("")
        .trimEnd()}…`;
};

const addressOrCoordinates = (
    result: PromiseSettledResult<string | undefined>,
    coordinates: [number, number],
) =>
    shortenAddress(
        result.status === "fulfilled" && result.value
            ? result.value
            : `${coordinates[0]}, ${coordinates[1]}`,
        NOTIFICATION_ADDRESS_MAX_LENGTH,
    );

export const sendAssignmentNotification = async (
    server: Bun.Server<unknown> | null,
    assignedJob: typeof job.$inferSelect,
) => {
    if (!server || !assignedJob.assignedDriverId) return;

    const [fromResult, toResult] = await Promise.allSettled([
        reverseGeocode(assignedJob.from),
        assignedJob.to ? reverseGeocode(assignedJob.to) : undefined,
    ]);
    const from = addressOrCoordinates(fromResult, assignedJob.from);
    const to = assignedJob.to
        ? addressOrCoordinates(toResult, assignedJob.to)
        : undefined;

    notify(
        server,
        {
            jobId: assignedJob.id,
            from,
            ...(to ? { to } : {}),
            ...(assignedJob.note ? { note: assignedJob.note } : {}),
        },
        assignedJob.assignedDriverId,
    );
};

export const notifyAssignedDriverInBackground = (
    server: Bun.Server<unknown> | null,
    assignedJob: typeof job.$inferSelect,
) => {
    void sendAssignmentNotification(server, assignedJob).catch((error) => {
        console.error(
            `Failed to notify driver about job ${assignedJob.id}`,
            error,
        );
    });
};
