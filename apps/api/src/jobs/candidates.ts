import type { job } from "../db/schema";
import { type RoutePoint, requestRoute } from "../geoservices/router";
import type { TrackInputMessage } from "../realtime/model";

type Job = typeof job.$inferSelect;
export type CandidateTarget = Pick<Job, "dueDate" | "from" | "to">;
type PickupKind = "preceding" | "target" | "following";

interface ScheduledPickup {
    dueDate: Date;
    jobId: string;
    kind: PickupKind;
    legIndex: number;
}

interface CandidateRoute {
    activeJob?: Job;
    points: RoutePoint[];
    precedingJobs: Job[];
    scheduledPickups: ScheduledPickup[];
    state: TrackInputMessage["state"];
    targetLegIndex: number;
}

export interface FollowingJobEstimate {
    jobId: string;
    estimatedPickupAt: Date;
    lateBySeconds: number;
}

export interface DriverCandidate {
    driverId: string;
    driverName: string;
    state: TrackInputMessage["state"];
    latitude: number;
    longitude: number;
    currentJobId?: string;
    precedingJobIds: string[];
    followingJobs: FollowingJobEstimate[];
    estimatedArrivalAt: Date;
    estimatedPickupAt: Date;
    routeDurationSeconds: number;
    waitingDurationSeconds: number;
    routeDistanceKilometers: number;
    approachDurationSeconds: number;
    approachDistanceKilometers: number;
    lateBySeconds: number;
    maximumFollowingLatenessSeconds: number;
}

export type RankingCriterion =
    | "followingJobDisruption"
    | "maximumFollowingLateness"
    | "targetLateness"
    | "estimatedPickupAt"
    | "approachDuration"
    | "estimatedArrivalAt";

export type RankingOutcome = "better" | "equal" | "worse";
export type RankingStepCode = `${RankingCriterion}.${RankingOutcome}`;
export type RankingValueUnit = "boolean" | "seconds" | "dateTime";
export type RankingSummaryCode =
    | "onlyEligibleDriver"
    | "rankedAhead"
    | "rankedBehind"
    | "tied";

export interface RankingTraceStep {
    criterion: RankingCriterion;
    outcome: RankingOutcome;
    code: RankingStepCode;
    values: {
        candidate: boolean | number | string;
        comparedTo: boolean | number | string;
        unit: RankingValueUnit;
    };
    message: string;
}

export interface RankingTrace {
    rank: number;
    summaryCode: RankingSummaryCode;
    summaryValues: {
        rank: number;
        comparedToDriverId?: string;
        comparedToDriverName?: string;
        decisiveCriterion?: RankingCriterion;
    };
    summary: string;
    comparedTo?: {
        driverId: string;
        driverName: string;
        relation: "ahead" | "behind" | "tied";
    };
    decisiveCriterion?: RankingCriterion;
    steps: RankingTraceStep[];
}

export interface RankedDriverCandidate extends DriverCandidate {
    rankingTrace: RankingTrace;
}

const toRoutePoint = (point: [number, number]): RoutePoint => ({
    latitude: point[0],
    longitude: point[1],
});

const findActiveJob = (jobs: Job[]) =>
    jobs
        .filter((candidate) => candidate.startedAt !== null)
        .sort(
            (left, right) =>
                (left.startedAt?.getTime() ?? 0) -
                (right.startedAt?.getTime() ?? 0),
        )[0];

const findBacklog = (jobs: Job[]) =>
    jobs
        .filter((candidate) => candidate.startedAt === null)
        .sort(
            (left, right) => left.dueDate.getTime() - right.dueDate.getTime(),
        );

const buildCandidateRoute = (
    target: CandidateTarget,
    position: RoutePoint,
    telemetryState: TrackInputMessage["state"],
    unfinishedJobs: Job[],
): CandidateRoute | undefined => {
    if (telemetryState === "away") return undefined;

    const activeJob = findActiveJob(unfinishedJobs);
    if (activeJob) {
        if (telemetryState !== "onTheWay" && telemetryState !== "occupied") {
            return undefined;
        }
    } else if (telemetryState !== "free") {
        return undefined;
    }

    const backlog = findBacklog(unfinishedJobs);
    const precedingBacklog = backlog.filter(
        (backlogJob) =>
            backlogJob.dueDate.getTime() <= target.dueDate.getTime(),
    );
    const followingBacklog = backlog.filter(
        (backlogJob) => backlogJob.dueDate.getTime() > target.dueDate.getTime(),
    );
    const precedingJobs = [
        ...(activeJob ? [activeJob] : []),
        ...precedingBacklog,
    ];

    if (precedingJobs.some((currentJob) => currentJob.to === null)) {
        return undefined;
    }
    if (followingBacklog.length > 0 && target.to === null) return undefined;
    if (
        followingBacklog
            .slice(0, -1)
            .some((currentJob) => currentJob.to === null)
    ) {
        return undefined;
    }

    const points = [position];
    const scheduledPickups: ScheduledPickup[] = [];

    const appendPoint = (point: [number, number]) => {
        points.push(toRoutePoint(point));
    };
    const appendPickup = (
        pickup: Pick<Job, "dueDate" | "from">,
        kind: PickupKind,
        jobId?: string,
    ) => {
        const legIndex = points.length - 1;
        appendPoint(pickup.from);
        scheduledPickups.push({
            dueDate: pickup.dueDate,
            jobId: jobId ?? "",
            kind,
            legIndex,
        });
    };

    if (activeJob) {
        if (telemetryState === "onTheWay") {
            appendPickup(activeJob, "preceding", activeJob.id);
        }
        appendPoint(activeJob.to as [number, number]);
    }

    for (const backlogJob of precedingBacklog) {
        appendPickup(backlogJob, "preceding", backlogJob.id);
        appendPoint(backlogJob.to as [number, number]);
    }

    const targetLegIndex = points.length - 1;
    appendPickup(target, "target");

    if (followingBacklog.length > 0) {
        appendPoint(target.to as [number, number]);
    }

    for (const [index, backlogJob] of followingBacklog.entries()) {
        appendPickup(backlogJob, "following", backlogJob.id);
        if (index < followingBacklog.length - 1) {
            appendPoint(backlogJob.to as [number, number]);
        }
    }

    return {
        activeJob,
        points,
        precedingJobs,
        scheduledPickups,
        state: telemetryState,
        targetLegIndex,
    };
};

const calculateSchedule = (
    route: CandidateRoute,
    legDurations: number[],
    target: CandidateTarget,
    now: Date,
) => {
    if (legDurations.length !== route.points.length - 1) return undefined;

    const pickupByLeg = new Map(
        route.scheduledPickups.map((pickup) => [pickup.legIndex, pickup]),
    );
    const followingJobs: FollowingJobEstimate[] = [];
    let time = now.getTime();
    let waitingDurationMilliseconds = 0;
    let estimatedArrivalAt: Date | undefined;
    let estimatedPickupAt: Date | undefined;
    let waitingDurationAtTarget: number | undefined;
    let lateBySeconds: number | undefined;

    for (const [legIndex, durationSeconds] of legDurations.entries()) {
        time += durationSeconds * 1000;

        const pickup = pickupByLeg.get(legIndex);
        if (!pickup) continue;

        const effectiveDueAt =
            pickup.kind === "target"
                ? Math.max(now.getTime(), target.dueDate.getTime())
                : pickup.dueDate.getTime();
        const arrivalAt = time;
        const pickupAt = Math.max(arrivalAt, effectiveDueAt);
        const pickupLatenessSeconds = Math.max(
            0,
            (arrivalAt - effectiveDueAt) / 1000,
        );
        waitingDurationMilliseconds += pickupAt - arrivalAt;
        time = pickupAt;

        if (pickup.kind === "target") {
            estimatedArrivalAt = new Date(arrivalAt);
            estimatedPickupAt = new Date(pickupAt);
            waitingDurationAtTarget = waitingDurationMilliseconds;
            lateBySeconds = pickupLatenessSeconds;
        } else if (pickup.kind === "following") {
            followingJobs.push({
                jobId: pickup.jobId,
                estimatedPickupAt: new Date(pickupAt),
                lateBySeconds: pickupLatenessSeconds,
            });
        }
    }

    if (
        !estimatedArrivalAt ||
        !estimatedPickupAt ||
        waitingDurationAtTarget === undefined ||
        lateBySeconds === undefined
    ) {
        return undefined;
    }

    return {
        estimatedArrivalAt,
        estimatedPickupAt,
        followingJobs,
        lateBySeconds,
        maximumFollowingLatenessSeconds: Math.max(
            0,
            ...followingJobs.map((followingJob) => followingJob.lateBySeconds),
        ),
        waitingDurationSeconds: waitingDurationAtTarget / 1000,
    };
};

export async function calculateDriverCandidate(
    driverId: string,
    driverName: string,
    telemetry: TrackInputMessage,
    target: CandidateTarget,
    unfinishedJobs: Job[],
    now: Date,
): Promise<DriverCandidate | undefined> {
    const route = buildCandidateRoute(
        target,
        {
            latitude: telemetry.latitude,
            longitude: telemetry.longitude,
        },
        telemetry.state,
        unfinishedJobs,
    );
    if (!route) return undefined;

    const { result } = await requestRoute(route.points);
    if (!("trip" in result)) return undefined;

    const approach = result.trip.legs[route.targetLegIndex]?.summary;
    if (!approach) return undefined;

    const schedule = calculateSchedule(
        route,
        result.trip.legs.map((leg) => leg.summary.time),
        target,
        now,
    );
    if (!schedule) return undefined;

    const routeToTarget = result.trip.legs.slice(0, route.targetLegIndex + 1);

    return {
        driverId,
        driverName,
        state: route.state,
        latitude: telemetry.latitude,
        longitude: telemetry.longitude,
        ...(route.activeJob ? { currentJobId: route.activeJob.id } : {}),
        precedingJobIds: route.precedingJobs.map(({ id }) => id),
        ...schedule,
        routeDurationSeconds: routeToTarget.reduce(
            (total, leg) => total + leg.summary.time,
            0,
        ),
        routeDistanceKilometers: routeToTarget.reduce(
            (total, leg) => total + leg.summary.length,
            0,
        ),
        approachDurationSeconds: approach.time,
        approachDistanceKilometers: approach.length,
    };
}

interface CandidateComparison {
    order: number;
    steps: RankingTraceStep[];
}

const comparisonOutcome = (order: number): RankingTraceStep["outcome"] =>
    order < 0 ? "better" : order > 0 ? "worse" : "equal";

const compareDriverCandidates = (
    candidate: DriverCandidate,
    other: DriverCandidate,
): CandidateComparison => {
    const steps: RankingTraceStep[] = [];
    const addStep = (
        criterion: RankingCriterion,
        order: number,
        values: RankingTraceStep["values"],
        messages: Record<RankingTraceStep["outcome"], string>,
    ) => {
        const outcome = comparisonOutcome(order);
        steps.push({
            criterion,
            outcome,
            code: `${criterion}.${outcome}`,
            values,
            message: messages[outcome],
        });
        return order;
    };

    const candidateDisruptsFollowingJobs =
        candidate.maximumFollowingLatenessSeconds > 0;
    const otherDisruptsFollowingJobs =
        other.maximumFollowingLatenessSeconds > 0;
    let order = addStep(
        "followingJobDisruption",
        Number(candidateDisruptsFollowingJobs) -
            Number(otherDisruptsFollowingJobs),
        {
            candidate: candidateDisruptsFollowingJobs,
            comparedTo: otherDisruptsFollowingJobs,
            unit: "boolean",
        },
        {
            better: `Keeps all following jobs on time; ${other.driverName} would delay at least one.`,
            equal: candidateDisruptsFollowingJobs
                ? `Like ${other.driverName}, delays at least one following job.`
                : `Like ${other.driverName}, keeps all following jobs on time.`,
            worse: `Would delay at least one following job; ${other.driverName} keeps all of them on time.`,
        },
    );
    if (order !== 0) return { order, steps };

    order = addStep(
        "maximumFollowingLateness",
        candidate.maximumFollowingLatenessSeconds -
            other.maximumFollowingLatenessSeconds,
        {
            candidate: candidate.maximumFollowingLatenessSeconds,
            comparedTo: other.maximumFollowingLatenessSeconds,
            unit: "seconds",
        },
        {
            better: `Worst following-job delay is ${candidate.maximumFollowingLatenessSeconds} seconds, compared with ${other.driverName} at ${other.maximumFollowingLatenessSeconds} seconds.`,
            equal: `Worst following-job delay matches ${other.driverName} at ${candidate.maximumFollowingLatenessSeconds} seconds.`,
            worse: `Worst following-job delay is ${candidate.maximumFollowingLatenessSeconds} seconds, compared with ${other.driverName} at ${other.maximumFollowingLatenessSeconds} seconds.`,
        },
    );
    if (order !== 0) return { order, steps };

    const candidateIsLate = candidate.lateBySeconds > 0;
    const otherIsLate = other.lateBySeconds > 0;
    order = addStep(
        "targetLateness",
        Number(candidateIsLate) - Number(otherIsLate),
        {
            candidate: candidateIsLate,
            comparedTo: otherIsLate,
            unit: "boolean",
        },
        {
            better: `Can pick up the target job on time; ${other.driverName} would be late.`,
            equal: candidateIsLate
                ? `Like ${other.driverName}, would be late for the target job.`
                : `Like ${other.driverName}, can pick up the target job on time.`,
            worse: `Would be late for the target job; ${other.driverName} can pick it up on time.`,
        },
    );
    if (order !== 0) return { order, steps };

    if (candidateIsLate) {
        order = addStep(
            "estimatedPickupAt",
            candidate.estimatedPickupAt.getTime() -
                other.estimatedPickupAt.getTime(),
            {
                candidate: candidate.estimatedPickupAt.toISOString(),
                comparedTo: other.estimatedPickupAt.toISOString(),
                unit: "dateTime",
            },
            {
                better: `Estimated pickup is ${candidate.estimatedPickupAt.toISOString()}, earlier than ${other.driverName} at ${other.estimatedPickupAt.toISOString()}.`,
                equal: `Estimated pickup matches ${other.driverName} at ${candidate.estimatedPickupAt.toISOString()}.`,
                worse: `Estimated pickup is ${candidate.estimatedPickupAt.toISOString()}, later than ${other.driverName} at ${other.estimatedPickupAt.toISOString()}.`,
            },
        );
        return { order, steps };
    }

    order = addStep(
        "approachDuration",
        candidate.approachDurationSeconds - other.approachDurationSeconds,
        {
            candidate: candidate.approachDurationSeconds,
            comparedTo: other.approachDurationSeconds,
            unit: "seconds",
        },
        {
            better: `Final approach takes ${candidate.approachDurationSeconds} seconds, shorter than ${other.driverName} at ${other.approachDurationSeconds} seconds.`,
            equal: `Final approach matches ${other.driverName} at ${candidate.approachDurationSeconds} seconds.`,
            worse: `Final approach takes ${candidate.approachDurationSeconds} seconds, longer than ${other.driverName} at ${other.approachDurationSeconds} seconds.`,
        },
    );
    if (order !== 0) return { order, steps };

    order = addStep(
        "estimatedArrivalAt",
        candidate.estimatedArrivalAt.getTime() -
            other.estimatedArrivalAt.getTime(),
        {
            candidate: candidate.estimatedArrivalAt.toISOString(),
            comparedTo: other.estimatedArrivalAt.toISOString(),
            unit: "dateTime",
        },
        {
            better: `Estimated arrival is ${candidate.estimatedArrivalAt.toISOString()}, earlier than ${other.driverName} at ${other.estimatedArrivalAt.toISOString()}.`,
            equal: `Estimated arrival matches ${other.driverName} at ${candidate.estimatedArrivalAt.toISOString()}.`,
            worse: `Estimated arrival is ${candidate.estimatedArrivalAt.toISOString()}, later than ${other.driverName} at ${other.estimatedArrivalAt.toISOString()}.`,
        },
    );
    return { order, steps };
};

const buildRankingTrace = (
    candidates: DriverCandidate[],
    index: number,
): RankingTrace => {
    const candidate = candidates[index];
    if (!candidate) throw new Error("Expected ranked candidate");

    const rank = index + 1;
    if (candidates.length === 1) {
        return {
            rank,
            summaryCode: "onlyEligibleDriver",
            summaryValues: { rank },
            summary: "Only eligible driver.",
            steps: [],
        };
    }

    const other = index === 0 ? candidates[1] : candidates[index - 1];
    if (!other) throw new Error("Expected comparison candidate");

    const comparison = compareDriverCandidates(candidate, other);
    const relation =
        comparison.order < 0
            ? "ahead"
            : comparison.order > 0
              ? "behind"
              : "tied";
    const decisiveStep = comparison.steps.findLast(
        ({ outcome }) => outcome !== "equal",
    );
    const summary = decisiveStep
        ? `Ranked ${relation} ${other.driverName}. ${decisiveStep.message}`
        : `Tied with ${other.driverName} on every ranking criterion; list order is used only for presentation.`;
    const summaryCode =
        relation === "ahead"
            ? "rankedAhead"
            : relation === "behind"
              ? "rankedBehind"
              : "tied";

    return {
        rank,
        summaryCode,
        summaryValues: {
            rank,
            comparedToDriverId: other.driverId,
            comparedToDriverName: other.driverName,
            ...(decisiveStep
                ? { decisiveCriterion: decisiveStep.criterion }
                : {}),
        },
        summary,
        comparedTo: {
            driverId: other.driverId,
            driverName: other.driverName,
            relation,
        },
        ...(decisiveStep ? { decisiveCriterion: decisiveStep.criterion } : {}),
        steps: comparison.steps,
    };
};

export function rankDriverCandidates(
    candidates: DriverCandidate[],
): RankedDriverCandidate[] {
    const sortedCandidates = candidates.sort(
        (left, right) => compareDriverCandidates(left, right).order,
    );

    return sortedCandidates.map((candidate, index) => ({
        ...candidate,
        rankingTrace: buildRankingTrace(sortedCandidates, index),
    }));
}
