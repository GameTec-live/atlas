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
            await db.insert(job).values(body);
            return { message: "Job created successfully" };
        },
        {
            body: JobModel.jobInsertModel,
            auth: true,
        },
    )
    .post(
        "/:id/assign",
        async ({ params, body, user }) => {
            console.log(
                "Assigning job",
                params.id,
                "to driver",
                user.id,
                "with body",
                body,
            );
        },
        {
            params: t.Object({
                id: t.String({ format: "uuid" }),
            }),
            body: JobModel.jobAssignModel,
            auth: true,
        },
    );
