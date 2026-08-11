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

export const sendAssignmentNotification = async (
    server: Bun.Server<unknown> | null,
    assignedJob: typeof job.$inferSelect,
) => {
    if (!server || !assignedJob.assignedDriverId) return;

    const [from, to] = await Promise.all([
        reverseGeocode(assignedJob.from),
        assignedJob.to ? reverseGeocode(assignedJob.to) : undefined,
    ]);

    if (!from || (assignedJob.to && !to)) {
        throw new Error("Reverse geocoding returned no address");
    }

    notify(
        server,
        {
            jobId: assignedJob.id,
            from: shortenAddress(from, NOTIFICATION_ADDRESS_MAX_LENGTH),
            ...(to
                ? {
                      to: shortenAddress(to, NOTIFICATION_ADDRESS_MAX_LENGTH),
                  }
                : {}),
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
