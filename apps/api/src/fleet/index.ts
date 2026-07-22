import { desc, eq } from "drizzle-orm";
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
            const vehicleData = await db
                .select()
                .from(vehicle)
                .where(eq(vehicle.id, vehicleId))
                .leftJoin(maintenance, eq(vehicle.id, maintenance.vehicleId))
                .orderBy(desc(maintenance.createdAt))
                .limit(1);

            if (vehicleData.length === 0) {
                return status(404, { error: "Vehicle not found" });
            }

            return vehicleData[0];
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
            await db.insert(vehicle).values(body);
            return { message: "Vehicle created successfully" };
        },
        {
            body: FleetModel.vehicleInsertModel,
            auth: true,
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
            auth: true,
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
            auth: true,
        },
    );
