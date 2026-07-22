import { Elysia } from "elysia";
import { authHandler } from "../authHandler";
import { type NotifyResponse, RealtimeModel } from "./model";

const BROADCAST_TRACK_TOPIC = "api:ws:track";
const BROADCAST_NOTIFY_TOPIC = "api:ws:notify:";

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

export const realtime = new Elysia({
    prefix: "/realtime",
    tags: ["realtime"],
    websocket: {
        idleTimeout: 60 * 5, // 5 minutes
    },
})
    .use(authHandler)
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
