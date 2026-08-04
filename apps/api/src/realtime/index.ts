import { eq } from "drizzle-orm";
import { Elysia } from "elysia";
import { authHandler } from "../authHandler";
import { db } from "../db";
import { vehicle } from "../db/schema";
import {
    type NotifyResponse,
    RealtimeModel,
    type TrackInputMessage,
} from "./model";

const BROADCAST_TRACK_TOPIC = "api:ws:track";
const BROADCAST_NOTIFY_TOPIC = "api:ws:notify:";
const TELEMETRY_PERSIST_INTERVAL_MS = 5 * 60 * 1000;

export function notify(
    server: Bun.Server<unknown> | null,
    notification: NotifyResponse,
    userId: string,
) {
    if (!server) return 0;

    return server.publish(
        BROADCAST_NOTIFY_TOPIC + userId,
        JSON.stringify(notification),
    );
}

export const trackCache = new Map<string, TrackInputMessage>();

export async function persistVehicleTelemetry() {
    const updates = [...trackCache.values()].flatMap((message) => {
        if (!message.vehicleId) return [];

        const telemetry = {
            ...(message.fuelLevel !== undefined
                ? { fuelLevel: message.fuelLevel }
                : {}),
            ...(message.odometer !== undefined
                ? { odometer: message.odometer }
                : {}),
        };

        if (Object.keys(telemetry).length === 0) return [];

        return [
            db
                .update(vehicle)
                .set(telemetry)
                .where(eq(vehicle.id, message.vehicleId)),
        ];
    });

    const results = await Promise.all(updates);
    return results.reduce((total, result) => total + (result.rowCount ?? 0), 0);
}

let telemetryPersistenceTimer: Timer | undefined;
let telemetryPersistenceInProgress = false;

function startTelemetryPersistence() {
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

function stopTelemetryPersistence() {
    clearInterval(telemetryPersistenceTimer);
    telemetryPersistenceTimer = undefined;
}

export const realtime = new Elysia({
    prefix: "/realtime",
    tags: ["realtime"],
    websocket: {
        idleTimeout: 60 * 5, // 5 minutes
    },
})
    .use(authHandler)
    .onStart(startTelemetryPersistence)
    .onStop(stopTelemetryPersistence)
    .get("/", () => {
        return {
            message: "Realtime endpoints: /track, /notify",
        };
    })
    .ws("/track", {
        open(ws) {
            ws.subscribe(BROADCAST_TRACK_TOPIC);
            ws.publish(BROADCAST_TRACK_TOPIC, {
                type: "connectionChange",
                userId: ws.data.user.id,
                userName: ws.data.user.name,
                state: "connected",
            });
        },
        message(ws, message) {
            ws.publish(BROADCAST_TRACK_TOPIC, {
                ...message,
                userId: ws.data.user.id,
                userName: ws.data.user.name,
            });
            trackCache.set(ws.data.user.id, message);
        },
        close(ws) {
            ws.unsubscribe(BROADCAST_TRACK_TOPIC);
            ws.publish(BROADCAST_TRACK_TOPIC, {
                type: "connectionChange",
                userId: ws.data.user.id,
                userName: ws.data.user.name,
                state: "disconnected",
            });
        },
        auth: true,
        body: RealtimeModel.trackInputMessage,
        response: RealtimeModel.realtimeResponse,
    })
    .ws("/notify", {
        open(ws) {
            ws.subscribe(BROADCAST_NOTIFY_TOPIC + ws.data.user.id);
        },
        close(ws) {
            ws.unsubscribe(BROADCAST_NOTIFY_TOPIC + ws.data.user.id);
        },
        response: RealtimeModel.notifyResponse,
        auth: true,
    });
