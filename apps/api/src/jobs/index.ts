import { eq } from "drizzle-orm";
import { Elysia } from "elysia";
import { authHandler } from "../authHandler";
import { db } from "../db";
import { job } from "../db/schema";

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
                .where(eq(job.assignedDriverId, user.id));
            return jobs;
        },
        {
            auth: true,
        },
    );
