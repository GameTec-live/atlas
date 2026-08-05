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

export function rankDriverCandidates(candidates: DriverCandidate[]) {
    return candidates.sort((left, right) => {
        const leftDisruptsFollowingJobs =
            left.maximumFollowingLatenessSeconds > 0;
        const rightDisruptsFollowingJobs =
            right.maximumFollowingLatenessSeconds > 0;

        if (leftDisruptsFollowingJobs !== rightDisruptsFollowingJobs) {
            return leftDisruptsFollowingJobs ? 1 : -1;
        }
        if (
            left.maximumFollowingLatenessSeconds !==
            right.maximumFollowingLatenessSeconds
        ) {
            return (
                left.maximumFollowingLatenessSeconds -
                right.maximumFollowingLatenessSeconds
            );
        }

        const leftIsLate = left.lateBySeconds > 0;
        const rightIsLate = right.lateBySeconds > 0;
        if (leftIsLate !== rightIsLate) return leftIsLate ? 1 : -1;

        if (leftIsLate) {
            return (
                left.estimatedPickupAt.getTime() -
                right.estimatedPickupAt.getTime()
            );
        }

        return (
            left.approachDurationSeconds - right.approachDurationSeconds ||
            left.estimatedArrivalAt.getTime() -
                right.estimatedArrivalAt.getTime()
        );
    });
}
