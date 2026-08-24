import { and, asc, desc, eq, inArray, isNotNull, isNull } from "drizzle-orm";
import { Elysia, status, t } from "elysia";
import { env } from "@/env";
import { authHandler, isAdmin } from "../authHandler";
import { db } from "../db";
import { job, user } from "../db/schema";
import { trackCache } from "../realtime/cache";
import {
    withReverseGeocodedAddress,
    withReverseGeocodedAddresses,
} from "./addresses";
import {
    type CandidateTarget,
    calculateDriverCandidate,
    rankDriverCandidates,
} from "./candidates";
import { JobModel } from "./model";
import { notifyAssignedDriverInBackground } from "./notifications";

const calculateCandidates = async (target: CandidateTarget) => {
    const trackedDrivers = [...trackCache.entries()];
    if (trackedDrivers.length === 0) return [];

    const driverIds = trackedDrivers.map(([driverId]) => driverId);
    const [unfinishedJobs, driverUsers] = await Promise.all([
        db
            .select()
            .from(job)
            .where(
                and(
                    inArray(job.assignedDriverId, driverIds),
                    isNull(job.completedAt),
                ),
            ),
        db
            .select({ id: user.id, name: user.name })
            .from(user)
            .where(inArray(user.id, driverIds)),
    ]);
    const driverNames = new Map(
        driverUsers.map((driverUser) => [driverUser.id, driverUser.name]),
    );
    const jobsByDriver = new Map<string, typeof unfinishedJobs>();
    for (const unfinishedJob of unfinishedJobs) {
        if (!unfinishedJob.assignedDriverId) continue;
        const driverJobs =
            jobsByDriver.get(unfinishedJob.assignedDriverId) ?? [];
        driverJobs.push(unfinishedJob);
        jobsByDriver.set(unfinishedJob.assignedDriverId, driverJobs);
    }

    const now = new Date();
    const candidates = await Promise.all(
        trackedDrivers.map(([driverId, telemetry]) => {
            const driverName = driverNames.get(driverId);
            if (!driverName) return undefined;

            return calculateDriverCandidate(
                driverId,
                driverName,
                telemetry,
                target,
                jobsByDriver.get(driverId) ?? [],
                now,
            );
        }),
    );

    return rankDriverCandidates(
        candidates.filter((candidate) => candidate !== undefined),
    );
};

export const jobs = new Elysia({
    prefix: "/jobs",
    tags: ["jobs"],
})
    .use(authHandler)
    .get(
        "/assigned",
        async ({ user, query }) => {
            const jobs = await db
                .select()
                .from(job)
                .where(eq(job.assignedDriverId, user.id))
                .orderBy(asc(job.dueDate), asc(job.startedAt));
            return query.geocode === undefined
                ? jobs
                : withReverseGeocodedAddresses(jobs);
        },
        {
            auth: true,
            query: JobModel.geocodeQuery,
        },
    )
    .get(
        "/current",
        async ({ user, query }) => {
            const [currentJob] = await db
                .select()
                .from(job)
                .where(
                    and(
                        eq(job.assignedDriverId, user.id),
                        isNotNull(job.startedAt),
                        isNull(job.completedAt),
                    ),
                )
                .orderBy(desc(job.startedAt))
                .limit(1);

            if (!currentJob) {
                return status(404, { error: "No current job" });
            }

            return query.geocode === undefined
                ? currentJob
                : withReverseGeocodedAddress(currentJob);
        },
        {
            auth: true,
            query: JobModel.geocodeQuery,
        },
    )
    .get(
        "/unassigned",
        async ({ query }) => {
            const jobs = await db
                .select()
                .from(job)
                .where(isNull(job.assignedDriverId))
                .orderBy(asc(job.dueDate), asc(job.createdAt));
            return query.geocode === undefined
                ? jobs
                : withReverseGeocodedAddresses(jobs);
        },
        {
            auth: true,
            query: JobModel.geocodeQuery,
        },
    )
    .get(
        "/unassigned-reduced",
        async ({ headers, query }) => {
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
                    .orderBy(asc(job.dueDate), asc(job.createdAt));
                return query.geocode === undefined
                    ? jobs
                    : withReverseGeocodedAddresses(jobs);
            }

            return status(401, { error: "Unauthorized" });
        },
        {
            headers: t.Object({
                authorization: t.Optional(t.String()),
            }),
            query: JobModel.geocodeQuery,
            detail: {
                security: [{ APIKeyAuth: [] }],
            },
        },
    )
    .get(
        "/jobtoken",
        ({ set }) => {
            set.headers["cache-control"] = "no-store";
            return { jobtoken: env.JOBTOKEN };
        },
        {
            admin: true,
            response: t.Object({ jobtoken: t.String() }),
        },
    )
    .post(
        "/create",
        async ({ body, server }) => {
            const [newJob] = await db.insert(job).values(body).returning();

            if (!newJob) {
                return status(500, { error: "Failed to create job" });
            }

            notifyAssignedDriverInBackground(server, newJob);

            return newJob;
        },
        {
            body: JobModel.jobInsertModel,
            auth: true,
        },
    )
    .post(
        "/candidates",
        ({ body }) =>
            calculateCandidates({
                from: body.from,
                to: body.to ?? null,
                dueDate: body.dueDate ?? new Date(),
            }),
        {
            body: JobModel.jobCandidateRequestModel,
            response: t.Array(JobModel.candidateModel),
            auth: true,
        },
    )
    .get(
        "/:id/candidates",
        async ({ params }) => {
            const [targetJob] = await db
                .select()
                .from(job)
                .where(eq(job.id, params.id))
                .limit(1);

            if (!targetJob) {
                return status(404, { error: "Job not found" });
            }
            if (targetJob.assignedDriverId !== null) {
                return status(409, { error: "Job is already assigned" });
            }
            if (targetJob.completedAt !== null) {
                return status(409, { error: "Job is already completed" });
            }

            return calculateCandidates(targetJob);
        },
        {
            params: t.Object({
                id: t.String({ format: "uuid" }),
            }),
            response: {
                200: t.Array(JobModel.candidateModel),
                404: t.Object({ error: t.String() }),
                409: t.Object({ error: t.String() }),
            },
            auth: true,
        },
    )
    .post(
        "/:id/assign",
        async ({ params, body, user, server }) => {
            const [updatedJob] = await db
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

            if (!updatedJob) {
                return status(404, { error: "Job not found" });
            }

            notifyAssignedDriverInBackground(server, updatedJob);

            return updatedJob;
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
        async ({ params, user }) => {
            if (!isAdmin(user.role)) {
                const [targetJob] = await db
                    .select()
                    .from(job)
                    .where(
                        and(
                            eq(job.id, params.id),
                            eq(job.assignedDriverId, user.id),
                        ),
                    )
                    .limit(1);

                if (!targetJob) {
                    return status(403, {
                        error: "You are not authorized to cancel this job",
                    });
                }
            }

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
    .delete(
        "/:id",
        async ({ params }) => {
            const deleteResult = await db
                .delete(job)
                .where(
                    and(eq(job.id, params.id), isNull(job.assignedDriverId)),
                );

            if (deleteResult.rowCount === 0) {
                return status(404, { error: "Unassigned job not found" });
            }

            return { message: "Job deleted successfully" };
        },
        {
            params: t.Object({
                id: t.String({ format: "uuid" }),
            }),
            admin: true,
        },
    )
    .get(
        "/:id",
        async ({ params, query }) => {
            const [foundJob] = await db
                .select()
                .from(job)
                .where(eq(job.id, params.id))
                .limit(1);

            if (!foundJob) {
                return status(404, { error: "Job not found" });
            }

            return query.geocode === undefined
                ? foundJob
                : withReverseGeocodedAddress(foundJob);
        },
        {
            params: t.Object({
                id: t.String({ format: "uuid" }),
            }),
            query: JobModel.geocodeQuery,
            auth: true,
        },
    )
    .get(
        "/all",
        async ({ query }) => {
            const jobs = await db
                .select({
                    id: job.id,
                    assignedDriverId: job.assignedDriverId,
                    vehicleId: job.vehicleId,
                    from: job.from,
                    to: job.to,
                    dueDate: job.dueDate,
                    note: job.note,
                    startedAt: job.startedAt,
                    completedAt: job.completedAt,
                    createdAt: job.createdAt,
                    updatedAt: job.updatedAt,
                    assignedDriverName: user.name,
                })
                .from(job)
                .leftJoin(user, eq(job.assignedDriverId, user.id))
                .orderBy(desc(job.createdAt))
                .where(
                    query.filter === "assigned"
                        ? isNotNull(job.assignedDriverId)
                        : query.filter === "unassigned"
                          ? isNull(job.assignedDriverId)
                          : undefined,
                );
            return query.geocode === undefined
                ? jobs
                : withReverseGeocodedAddresses(jobs);
        },
        {
            query: t.Object({
                geocode: t.Optional(t.String()),
                filter: t.Optional(
                    t.Enum({
                        all: "all",
                        assigned: "assigned",
                        unassigned: "unassigned",
                    }),
                ),
            }),
            admin: true,
        },
    );
