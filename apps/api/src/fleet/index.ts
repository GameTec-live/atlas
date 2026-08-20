import { and, desc, eq, isNull } from "drizzle-orm";
import { Elysia, status, t } from "elysia";
import { authHandler } from "../authHandler";
import { db } from "../db";
import { maintenance, vehicle } from "../db/schema";
import { FleetModel } from "./model";

export const fleet = new Elysia({
    prefix: "/fleet",
    tags: ["fleet"],
})
    .use(authHandler)
    .get(
        "/vehicles",
        async () => {
            const vehicles = await db
                .selectDistinctOn([vehicle.id])
                .from(vehicle)
                .leftJoin(maintenance, eq(vehicle.id, maintenance.vehicleId))
                .orderBy(vehicle.id, desc(maintenance.createdAt));
            return vehicles;
        },
        {
            auth: true,
        },
    )
    .get(
        "/vehicles/:id",
        async ({ params }) => {
            const vehicleId = params.id;
            const [foundVehicle] = await db
                .select()
                .from(vehicle)
                .where(eq(vehicle.id, vehicleId))
                .leftJoin(maintenance, eq(vehicle.id, maintenance.vehicleId))
                .orderBy(desc(maintenance.createdAt))
                .limit(1);

            if (!foundVehicle) {
                return status(404, { error: "Vehicle not found" });
            }

            return foundVehicle;
        },
        {
            params: t.Object({
                id: t.String({ format: "uuid" }),
            }),
            auth: true,
        },
    )
    .post(
        "/vehicles",
        async ({ body }) => {
            const [newVehicle] = await db
                .insert(vehicle)
                .values(body)
                .returning();

            if (!newVehicle) {
                return status(500, { error: "Failed to create vehicle" });
            }

            return newVehicle;
        },
        {
            body: FleetModel.vehicleInsertModel,
            admin: true,
        },
    )
    .put(
        "/vehicles/:id",
        async ({ params, body }) => {
            const vehicleId = params.id;
            const updateResult = await db
                .update(vehicle)
                .set(body)
                .where(eq(vehicle.id, vehicleId));

            if (updateResult.rowCount === 0) {
                return status(404, { error: "Vehicle not found" });
            }

            return { message: "Vehicle updated successfully" };
        },
        {
            params: t.Object({
                id: t.String({ format: "uuid" }),
            }),
            body: FleetModel.vehicleUpdateModel,
            admin: true,
        },
    )
    .delete(
        "/vehicles/:id",
        async ({ params }) => {
            const vehicleId = params.id;
            const deleteResult = await db
                .delete(vehicle)
                .where(eq(vehicle.id, vehicleId));

            if (deleteResult.rowCount === 0) {
                return status(404, { error: "Vehicle not found" });
            }

            return { message: "Vehicle deleted successfully" };
        },
        {
            params: t.Object({
                id: t.String({ format: "uuid" }),
            }),
            admin: true,
        },
    )
    .get(
        "/vehicles/:id/maintenance",
        async ({ params }) =>
            db
                .select()
                .from(maintenance)
                .where(eq(maintenance.vehicleId, params.id))
                .orderBy(desc(maintenance.createdAt)),
        {
            params: t.Object({
                id: t.String({ format: "uuid" }),
            }),
            admin: true,
        },
    )
    .post(
        "/vehicles/:id/maintenance",
        async ({ params, body }) => {
            const [newMaintenance] = await db
                .insert(maintenance)
                .values({ vehicleId: params.id, ...body })
                .returning();

            if (!newMaintenance) {
                return status(500, {
                    error: "Failed to create maintenance record",
                });
            }

            return newMaintenance;
        },
        {
            params: t.Object({
                id: t.String({ format: "uuid" }),
            }),
            body: FleetModel.maintenanceInsertModel,
            admin: true,
        },
    )
    .get(
        "/fingerprint/:fingerprint",
        async ({ params }) => {
            const fingerprint = params.fingerprint;
            const [vehicleData] = await db
                .select()
                .from(vehicle)
                .where(eq(vehicle.fingerprint, fingerprint));

            if (!vehicleData) {
                return status(404, { error: "Vehicle not found" });
            }
            return vehicleData;
        },
        {
            params: t.Object({
                fingerprint: t.String(),
            }),
            auth: true,
        },
    )
    .get(
        "/fingerprint/candidates",
        async () => {
            const candidates = await db
                .select({
                    id: vehicle.id,
                    brand: vehicle.brand,
                    model: vehicle.model,
                    year: vehicle.year,
                    licensePlate: vehicle.licensePlate,
                })
                .from(vehicle)
                .where(isNull(vehicle.fingerprint));
            return candidates;
        },
        {
            auth: true,
        },
    )
    .post(
        "/fingerprint/pair",
        async ({ body }) => {
            const { vehicleId, fingerprint } = body;
            const updateResult = await db
                .update(vehicle)
                .set({ fingerprint })
                .where(
                    and(eq(vehicle.id, vehicleId), isNull(vehicle.fingerprint)),
                );

            if (updateResult.rowCount === 0) {
                return status(404, {
                    error: "Vehicle not found or already paired",
                });
            }

            return { message: "Fingerprint paired successfully" };
        },
        {
            body: t.Object({
                vehicleId: t.String({ format: "uuid" }),
                fingerprint: t.String(),
            }),
            auth: true,
        },
    );
