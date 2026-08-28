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

const rankingCriterionModel = t.Enum({
    followingJobDisruption: "followingJobDisruption",
    maximumFollowingLateness: "maximumFollowingLateness",
    targetLateness: "targetLateness",
    estimatedPickupAt: "estimatedPickupAt",
    approachDuration: "approachDuration",
    estimatedArrivalAt: "estimatedArrivalAt",
});

const rankingOutcomeModel = t.Enum({
    better: "better",
    equal: "equal",
    worse: "worse",
});

export const JobModel = {
    jobInsertModel: t.Omit(jobInsertModel, ["startedAt", "completedAt"]),
    jobUpdateModel: t.Partial(jobInsertModel),
    jobAssignModel: t.Optional(
        t.Pick(t.Partial(jobInsertModel), [
            "assignedDriverId",
            "dueDate",
            "to",
            "note",
        ]),
    ),
    jobStartModel: t.Optional(
        t.Pick(t.Partial(jobInsertModel), ["to", "vehicleId"]),
    ),
    jobCandidateRequestModel: t.Pick(jobInsertModel, ["from", "to", "dueDate"]),
    geocodeQuery: t.Object({
        geocode: t.Optional(t.String()),
    }),
    candidateModel: t.Object({
        driverId: t.String(),
        driverName: t.String(),
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
        rankingTrace: t.Object({
            rank: t.Integer({ minimum: 1 }),
            summaryCode: t.Enum({
                onlyEligibleDriver: "onlyEligibleDriver",
                rankedAhead: "rankedAhead",
                rankedBehind: "rankedBehind",
                tied: "tied",
            }),
            summaryValues: t.Object({
                rank: t.Integer({ minimum: 1 }),
                comparedToDriverId: t.Optional(t.String()),
                comparedToDriverName: t.Optional(t.String()),
                decisiveCriterion: t.Optional(rankingCriterionModel),
            }),
            summary: t.String(),
            comparedTo: t.Optional(
                t.Object({
                    driverId: t.String(),
                    driverName: t.String(),
                    relation: t.Enum({
                        ahead: "ahead",
                        behind: "behind",
                        tied: "tied",
                    }),
                }),
            ),
            decisiveCriterion: t.Optional(rankingCriterionModel),
            steps: t.Array(
                t.Object({
                    criterion: rankingCriterionModel,
                    outcome: rankingOutcomeModel,
                    code: t.String({
                        pattern:
                            "^(followingJobDisruption|maximumFollowingLateness|targetLateness|estimatedPickupAt|approachDuration|estimatedArrivalAt)\\.(better|equal|worse)$",
                    }),
                    values: t.Object({
                        candidate: t.Union([
                            t.Boolean(),
                            t.Number(),
                            t.String(),
                        ]),
                        comparedTo: t.Union([
                            t.Boolean(),
                            t.Number(),
                            t.String(),
                        ]),
                        unit: t.Enum({
                            boolean: "boolean",
                            seconds: "seconds",
                            dateTime: "dateTime",
                        }),
                    }),
                    message: t.String(),
                }),
            ),
        }),
    }),
} as const;
