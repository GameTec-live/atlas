import { asc, eq, isNull } from "drizzle-orm";
import { Elysia, status, t } from "elysia";
import { env } from "@/env";
import { authHandler } from "../authHandler";
import { db } from "../db";
import { dbModel } from "../db/model";
import { job } from "../db/schema";
import { JobModel } from "./model";

export const jobs = new Elysia({
    prefix: "/jobs",
    tags: ["jobs"],
})
    .use(authHandler)
    .get(
        "/assigned",
        async ({ user }) => {
            const jobs = await db
                .select()
                .from(job)
                .where(eq(job.assignedDriverId, user.id))
                .orderBy(asc(job.dueDate));
            return jobs;
        },
        {
            auth: true,
        },
    )
    .get(
        "/unassigned",
        async () => {
            const jobs = await db
                .select()
                .from(job)
                .where(isNull(job.assignedDriverId))
                .orderBy(asc(job.dueDate));
            return jobs;
        },
        {
            auth: true,
        },
    )
    .get(
        "/unassigned-reduced",
        async ({ headers }) => {
            if (env.JOBTOKEN === headers.authorization) {
                const jobs = await db
                    .select({
                        id: job.id,
                        from: job.from,
                        to: job.to,
                        dueDate: job.dueDate,
                        note: job.note,
                    })
                    .from(job)
                    .where(isNull(job.assignedDriverId))
                    .orderBy(asc(job.dueDate));
                return jobs;
            }

            return status(401, { error: "Unauthorized" });
        },
        {
            headers: t.Object({
                authorization: t.Optional(t.String()),
            }),
            detail: {
                security: [{ APIKeyAuth: [] }],
            },
        },
    )
    .post(
        "/create",
        async ({ body }) => {
            const newJob = await db.insert(job).values(body).returning();
            return newJob;
        },
        {
            body: JobModel.jobInsertModel,
            auth: true,
        },
    )
    .post(
        "/:id/assign",
        async ({ params, body, user }) => {
            const updatedJobs = await db
                .update(job)
                .set({
                    assignedDriverId: body
                        ? (body.assignedDriverId ?? user.id)
                        : user.id,
                    ...(body
                        ? body.dueDate
                            ? { dueDate: body.dueDate }
                            : {}
                        : {}),
                    ...(body ? (body.to ? { to: body.to } : {}) : {}),
                })
                .where(eq(job.id, params.id))
                .returning();
            return updatedJobs;
        },
        {
            params: t.Object({
                id: t.String({ format: "uuid" }),
            }),
            body: JobModel.jobAssignModel,
            auth: true,
        },
    )
    .post(
        "/:id/start",
        async ({ params, body }) => {
            const updateResult = await db
                .update(job)
                .set({
                    startedAt: new Date(),
                    ...(body ? (body.to ? { to: body.to } : {}) : {}),
                    ...(body
                        ? body.vehicleId
                            ? { vehicleId: body.vehicleId }
                            : {}
                        : {}),
                })
                .where(eq(job.id, params.id));

            if (updateResult.rowCount === 0) {
                return status(404, { error: "Job not found" });
            }

            return { message: "Job started successfully" };
        },
        {
            params: t.Object({
                id: t.String({ format: "uuid" }),
            }),
            body: JobModel.jobStartModel,
            auth: true,
        },
    )
    .post(
        "/:id/complete",
        async ({ params }) => {
            const updateResult = await db
                .update(job)
                .set({ completedAt: new Date() })
                .where(eq(job.id, params.id));

            if (updateResult.rowCount === 0) {
                return status(404, { error: "Job not found" });
            }

            return { message: "Job completed successfully" };
        },
        {
            params: t.Object({
                id: t.String({ format: "uuid" }),
            }),
            auth: true,
        },
    )
    .post(
        "/:id/cancel",
        async ({ params }) => {
            const updateResult = await db
                .update(job)
                .set({
                    assignedDriverId: null,
                    startedAt: null,
                    completedAt: null,
                    vehicleId: null,
                })
                .where(eq(job.id, params.id));

            if (updateResult.rowCount === 0) {
                return status(404, { error: "Job not found" });
            }

            return { message: "Job canceled successfully" };
        },
        {
            params: t.Object({
                id: t.String({ format: "uuid" }),
            }),
            auth: true,
        },
    )
    .put(
        "/:id",
        async ({ params, body }) => {
            const updateResult = await db
                .update(job)
                .set(body)
                .where(eq(job.id, params.id));

            if (updateResult.rowCount === 0) {
                return status(404, { error: "Job not found" });
            }

            return { message: "Job updated successfully" };
        },
        {
            params: t.Object({
                id: t.String({ format: "uuid" }),
            }),
            body: JobModel.jobUpdateModel,
            auth: true,
        },
    )
    .get(
        "/:id",
        async ({ params }) => {
            const jobs = await db
                .select()
                .from(job)
                .where(eq(job.id, params.id))
                .limit(1);

            if (jobs.length === 0) {
                return status(404, { error: "Job not found" });
            }

            return jobs[0];
        },
        {
            params: t.Object({
                id: t.String({ format: "uuid" }),
            }),
            auth: true,
        },
    );
