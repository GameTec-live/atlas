import { t } from "elysia";
import { dbModel } from "../db/model";

const jobInsertModel = t.Object({
    ...t.Omit(t.Object(dbModel.insert.job), ["id", "createdAt", "updatedAt"])
        .properties,
    to: t.Optional(
        t.Tuple([
            t.Number({ minimum: -90, maximum: 90 }),
            t.Number({ minimum: -180, maximum: 180 }),
        ]),
    ),
    from: t.Tuple([
        t.Number({ minimum: -90, maximum: 90 }),
        t.Number({ minimum: -180, maximum: 180 }),
    ]),
});

export const JobModel = {
    jobInsertModel: t.Omit(jobInsertModel, ["startedAt", "completedAt"]),
    jobUpdateModel: t.Partial(jobInsertModel),
    jobAssignModel: t.Optional(
        t.Pick(t.Partial(jobInsertModel), [
            "assignedDriverId",
            "dueDate",
            "to",
        ]),
    ),
    jobStartModel: t.Optional(
        t.Pick(t.Partial(jobInsertModel), ["to", "vehicleId"]),
    ),
    jobCandidateRequestModel: t.Pick(jobInsertModel, ["from", "to", "dueDate"]),
    candidateModel: t.Object({
        driverId: t.String(),
        state: t.Enum({
            free: "free",
            onTheWay: "onTheWay",
            occupied: "occupied",
            away: "away",
        }),
        latitude: t.Number({ minimum: -90, maximum: 90 }),
        longitude: t.Number({ minimum: -180, maximum: 180 }),
        currentJobId: t.Optional(t.String({ format: "uuid" })),
        precedingJobIds: t.Array(t.String({ format: "uuid" })),
        followingJobs: t.Array(
            t.Object({
                jobId: t.String({ format: "uuid" }),
                estimatedPickupAt: t.Date(),
                lateBySeconds: t.Number({ minimum: 0 }),
            }),
        ),
        estimatedArrivalAt: t.Date(),
        estimatedPickupAt: t.Date(),
        routeDurationSeconds: t.Number({ minimum: 0 }),
        waitingDurationSeconds: t.Number({ minimum: 0 }),
        routeDistanceKilometers: t.Number({ minimum: 0 }),
        approachDurationSeconds: t.Number({ minimum: 0 }),
        approachDistanceKilometers: t.Number({ minimum: 0 }),
        lateBySeconds: t.Number({ minimum: 0 }),
        maximumFollowingLatenessSeconds: t.Number({ minimum: 0 }),
    }),
} as const;
