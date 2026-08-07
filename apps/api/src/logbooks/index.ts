import { between, eq, gte, lte } from "drizzle-orm";
import { Elysia, status, t } from "elysia";
import { authHandler } from "../authHandler";
import { db } from "../db";
import { logbook, user, vehicle } from "../db/schema";
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
    )
    .get(
        "/",
        async () => {
            const logbookEntries = await db
                .select({
                    id: logbook.id,
                    vehicleId: logbook.vehicleId,
                    driverId: logbook.driverId,
                    driverName: user.name,
                    startOdometer: logbook.startOdometer,
                    endOdometer: logbook.endOdometer,
                    startedAt: logbook.startedAt,
                    endedAt: logbook.endedAt,
                    revenue: logbook.revenue,
                    createdAt: logbook.createdAt,
                    updatedAt: logbook.updatedAt,
                    vehicle: {
                        id: vehicle.id,
                        licensePlate: vehicle.licensePlate,
                        brand: vehicle.brand,
                        model: vehicle.model,
                        year: vehicle.year,
                    },
                })
                .from(logbook)
                .leftJoin(vehicle, eq(logbook.vehicleId, vehicle.id))
                .leftJoin(user, eq(logbook.driverId, user.id));

            return logbookEntries;
        },
        {
            admin: true,
        },
    )
    .get(
        "/:id",
        async ({ params }) => {
            const [logbookEntry] = await db
                .select({
                    id: logbook.id,
                    vehicleId: logbook.vehicleId,
                    driverId: logbook.driverId,
                    driverName: user.name,
                    startOdometer: logbook.startOdometer,
                    endOdometer: logbook.endOdometer,
                    startedAt: logbook.startedAt,
                    endedAt: logbook.endedAt,
                    revenue: logbook.revenue,
                    createdAt: logbook.createdAt,
                    updatedAt: logbook.updatedAt,
                    vehicle: {
                        id: vehicle.id,
                        licensePlate: vehicle.licensePlate,
                        brand: vehicle.brand,
                        model: vehicle.model,
                        year: vehicle.year,
                    },
                })
                .from(logbook)
                .leftJoin(vehicle, eq(logbook.vehicleId, vehicle.id))
                .leftJoin(user, eq(logbook.driverId, user.id))
                .where(eq(logbook.id, params.id));

            if (!logbookEntry) {
                return status(404, { error: "Logbook entry not found" });
            }

            return logbookEntry;
        },
        {
            params: t.Object({
                id: t.String({ format: "uuid" }),
            }),
            admin: true,
        },
    )
    .get(
        "/vehicle/:vehicleId",
        async ({ params }) => {
            const logbookEntries = await db
                .select({
                    id: logbook.id,
                    vehicleId: logbook.vehicleId,
                    driverId: logbook.driverId,
                    driverName: user.name,
                    startOdometer: logbook.startOdometer,
                    endOdometer: logbook.endOdometer,
                    startedAt: logbook.startedAt,
                    endedAt: logbook.endedAt,
                    revenue: logbook.revenue,
                    createdAt: logbook.createdAt,
                    updatedAt: logbook.updatedAt,
                    vehicle: {
                        id: vehicle.id,
                        licensePlate: vehicle.licensePlate,
                        brand: vehicle.brand,
                        model: vehicle.model,
                        year: vehicle.year,
                    },
                })
                .from(logbook)
                .leftJoin(vehicle, eq(logbook.vehicleId, vehicle.id))
                .leftJoin(user, eq(logbook.driverId, user.id))
                .where(eq(logbook.vehicleId, params.vehicleId));
            return logbookEntries;
        },
        {
            params: t.Object({
                vehicleId: t.String({ format: "uuid" }),
            }),
            admin: true,
        },
    )
    .get(
        "/driver/:driverId",
        async ({ params }) => {
            const logbookEntries = await db
                .select({
                    id: logbook.id,
                    vehicleId: logbook.vehicleId,
                    driverId: logbook.driverId,
                    driverName: user.name,
                    startOdometer: logbook.startOdometer,
                    endOdometer: logbook.endOdometer,
                    startedAt: logbook.startedAt,
                    endedAt: logbook.endedAt,
                    revenue: logbook.revenue,
                    createdAt: logbook.createdAt,
                    updatedAt: logbook.updatedAt,
                    vehicle: {
                        id: vehicle.id,
                        licensePlate: vehicle.licensePlate,
                        brand: vehicle.brand,
                        model: vehicle.model,
                        year: vehicle.year,
                    },
                })
                .from(logbook)
                .leftJoin(vehicle, eq(logbook.vehicleId, vehicle.id))
                .leftJoin(user, eq(logbook.driverId, user.id))
                .where(eq(logbook.driverId, params.driverId));
            return logbookEntries;
        },
        {
            params: t.Object({
                driverId: t.String(),
            }),
            admin: true,
        },
    )
    .get(
        "/date",
        async ({ query }) => {
            const { startDate, endDate, exactDate } = query;

            const logbookEntries = await db
                .select({
                    id: logbook.id,
                    vehicleId: logbook.vehicleId,
                    driverId: logbook.driverId,
                    driverName: user.name,
                    startOdometer: logbook.startOdometer,
                    endOdometer: logbook.endOdometer,
                    startedAt: logbook.startedAt,
                    endedAt: logbook.endedAt,
                    revenue: logbook.revenue,
                    createdAt: logbook.createdAt,
                    updatedAt: logbook.updatedAt,
                    vehicle: {
                        id: vehicle.id,
                        licensePlate: vehicle.licensePlate,
                        brand: vehicle.brand,
                        model: vehicle.model,
                        year: vehicle.year,
                    },
                })
                .from(logbook)
                .leftJoin(vehicle, eq(logbook.vehicleId, vehicle.id))
                .leftJoin(user, eq(logbook.driverId, user.id))
                .where((logbook) => {
                    if (exactDate) {
                        return eq(logbook.startedAt, exactDate);
                    } else if (startDate && endDate) {
                        return between(logbook.startedAt, startDate, endDate);
                    } else if (startDate) {
                        return gte(logbook.startedAt, startDate);
                    } else if (endDate) {
                        return lte(logbook.startedAt, endDate);
                    }

                    const d = new Date();
                    d.setHours(0, 0, 0, 0);
                    return gte(logbook.startedAt, d);
                });

            return logbookEntries;
        },
        {
            query: t.Object({
                startDate: t.Optional(t.Date()),
                endDate: t.Optional(t.Date()),
                exactDate: t.Optional(t.Date()),
            }),
            admin: true,
        },
    );
