import { asc, eq } from "drizzle-orm";
import { Elysia, t } from "elysia";
import { authHandler } from "../authHandler";
import { db } from "../db";
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
    );
