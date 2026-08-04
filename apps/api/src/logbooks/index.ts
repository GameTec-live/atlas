import { Elysia, status } from "elysia";
import { authHandler } from "../authHandler";
import { db } from "../db";
import { logbook } from "../db/schema";
import { resolveFingerprint } from "../util/fingerprint";
import { LogbooksModel } from "./model";

export const logbooks = new Elysia({
    prefix: "/logbooks",
    tags: ["logbooks"],
})
    .use(authHandler)
    .post(
        "/submit",
        async ({ body, user }) => {
            if (!body.vehicleId && !body.vehicleFingerprint) {
                return status(
                    400,
                    "Either vehicleId or vehicleFingerprint must be provided",
                );
            }

            const vehicleId = body.vehicleId
                ? body.vehicleId
                : await resolveFingerprint(body.vehicleFingerprint ?? "");

            if (!vehicleId) {
                return status(404, "Vehicle not found");
            }

            const [logbookEntry] = await db
                .insert(logbook)
                .values({
                    vehicleId,
                    driverId: user.id,
                    startOdometer: body.startOdometer,
                    endOdometer: body.endOdometer,
                    startedAt: body.startedAt,
                    endedAt: body.endedAt,
                    revenue: body.revenue,
                })
                .returning();

            if (!logbookEntry) {
                return status(500, "Failed to create logbook entry");
            }

            return logbookEntry;
        },
        {
            body: LogbooksModel.submitModel,
            auth: true,
        },
    );
