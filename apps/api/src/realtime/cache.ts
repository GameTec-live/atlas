import { eq } from "drizzle-orm";
import { db } from "../db";
import { vehicle } from "../db/schema";
import type { TrackInputMessage } from "./model";

const TELEMETRY_PERSIST_INTERVAL_MS = 5 * 60 * 1000;

export const trackCache = new Map<string, TrackInputMessage>();

type PendingTelemetryField = {
    revision: number;
    value: number;
};

type PendingVehicleTelemetry = {
    source: TrackInputMessage;
    vehicleId: string;
    fuelLevel?: PendingTelemetryField;
    odometer?: PendingTelemetryField;
};

const pendingVehicleTelemetry = new Map<string, PendingVehicleTelemetry>();
const cleanTrackCacheEntries = new WeakSet<TrackInputMessage>();
let telemetryRevision = 0;

const nextTelemetryField = (value: number): PendingTelemetryField => ({
    revision: ++telemetryRevision,
    value,
});

export function cacheTrackMessage(userId: string, message: TrackInputMessage) {
    const previous = trackCache.get(userId);
    const previousPending = pendingVehicleTelemetry.get(userId);
    const vehicleChanged =
        message.vehicleId !== undefined &&
        message.vehicleId !== previous?.vehicleId;
    const vehicleId = message.vehicleId ?? previous?.vehicleId;
    const fuelLevel = vehicleChanged
        ? message.fuelLevel
        : (message.fuelLevel ?? previous?.fuelLevel);
    const odometer = vehicleChanged
        ? message.odometer
        : (message.odometer ?? previous?.odometer);

    const cachedMessage = {
        ...message,
        ...(vehicleId !== undefined ? { vehicleId } : {}),
        ...(fuelLevel !== undefined ? { fuelLevel } : {}),
        ...(odometer !== undefined ? { odometer } : {}),
    };
    trackCache.set(userId, cachedMessage);

    const carriedPendingTelemetry =
        !vehicleChanged &&
        previousPending !== undefined &&
        previousPending.source === previous &&
        previousPending.vehicleId === vehicleId
            ? previousPending
            : undefined;
    const pending = vehicleId
        ? {
              source: cachedMessage,
              vehicleId,
              fuelLevel:
                  message.fuelLevel !== undefined
                      ? nextTelemetryField(message.fuelLevel)
                      : carriedPendingTelemetry?.fuelLevel,
              odometer:
                  message.odometer !== undefined
                      ? nextTelemetryField(message.odometer)
                      : carriedPendingTelemetry?.odometer,
          }
        : undefined;

    if (pending?.fuelLevel || pending?.odometer) {
        pendingVehicleTelemetry.set(userId, pending);
    } else {
        pendingVehicleTelemetry.delete(userId);
        cleanTrackCacheEntries.add(cachedMessage);
    }
}

export function deleteCachedTrackMessage(userId: string) {
    trackCache.delete(userId);
    pendingVehicleTelemetry.delete(userId);
}

function getPendingVehicleTelemetry(
    userId: string,
    message: TrackInputMessage,
) {
    const existing = pendingVehicleTelemetry.get(userId);
    if (existing?.source === message) return existing;

    pendingVehicleTelemetry.delete(userId);
    if (cleanTrackCacheEntries.has(message) || !message.vehicleId) {
        return undefined;
    }

    const pending = {
        source: message,
        vehicleId: message.vehicleId,
        ...(message.fuelLevel !== undefined
            ? { fuelLevel: nextTelemetryField(message.fuelLevel) }
            : {}),
        ...(message.odometer !== undefined
            ? { odometer: nextTelemetryField(message.odometer) }
            : {}),
    };

    if (!pending.fuelLevel && !pending.odometer) {
        cleanTrackCacheEntries.add(message);
        return undefined;
    }

    pendingVehicleTelemetry.set(userId, pending);
    return pending;
}

function acknowledgeVehicleTelemetry(
    userId: string,
    flushed: PendingVehicleTelemetry,
) {
    const currentMessage = trackCache.get(userId);
    const current = pendingVehicleTelemetry.get(userId);
    if (
        !currentMessage ||
        !current ||
        current.source !== currentMessage ||
        current.vehicleId !== flushed.vehicleId
    ) {
        return;
    }

    if (current.fuelLevel?.revision === flushed.fuelLevel?.revision) {
        current.fuelLevel = undefined;
    }
    if (current.odometer?.revision === flushed.odometer?.revision) {
        current.odometer = undefined;
    }

    if (!current.fuelLevel && !current.odometer) {
        pendingVehicleTelemetry.delete(userId);
        cleanTrackCacheEntries.add(currentMessage);
    }
}

export async function persistVehicleTelemetry() {
    const updates = [...trackCache.entries()].flatMap(([userId, message]) => {
        const pending = getPendingVehicleTelemetry(userId, message);
        if (!pending) return [];

        const telemetry = {
            ...(pending.fuelLevel
                ? { fuelLevel: pending.fuelLevel.value }
                : {}),
            ...(pending.odometer ? { odometer: pending.odometer.value } : {}),
        };

        return [
            (async () => {
                const result = await db
                    .update(vehicle)
                    .set(telemetry)
                    .where(eq(vehicle.id, pending.vehicleId));

                acknowledgeVehicleTelemetry(userId, pending);

                return result;
            })(),
        ];
    });

    const results = await Promise.all(updates);
    return results.reduce((total, result) => total + (result.rowCount ?? 0), 0);
}

let telemetryPersistenceTimer: Timer | undefined;
let telemetryPersistenceInProgress = false;

export function startTelemetryPersistence() {
    if (telemetryPersistenceTimer) return;

    telemetryPersistenceTimer = setInterval(async () => {
        if (telemetryPersistenceInProgress) return;

        telemetryPersistenceInProgress = true;
        try {
            await persistVehicleTelemetry();
        } catch (error) {
            console.error("Failed to persist vehicle telemetry:", error);
        } finally {
            telemetryPersistenceInProgress = false;
        }
    }, TELEMETRY_PERSIST_INTERVAL_MS);
    telemetryPersistenceTimer.unref();
}

export function stopTelemetryPersistence() {
    clearInterval(telemetryPersistenceTimer);
    telemetryPersistenceTimer = undefined;
}
