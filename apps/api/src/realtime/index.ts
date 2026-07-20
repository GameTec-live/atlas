import { Elysia } from "elysia";
import { authHandler } from "../authHandler";
import { RealtimeModel } from "./model";

const BROADCAST_TRACK_TOPIC = "api:ws:track";

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
            message: "Realtime endpoints: /track",
        };
    })
    .ws("/track", {
        open(ws) {
            ws.subscribe(BROADCAST_TRACK_TOPIC);
            ws.publish(BROADCAST_TRACK_TOPIC, {
                type: "connectionChange",
                userId: ws.data.user.id,
                state: "connected",
            });
        },
        message(ws, message) {
            ws.publish(BROADCAST_TRACK_TOPIC, {
                ...message,
                userId: ws.data.user.id,
            });
        },
        close(ws) {
            ws.unsubscribe(BROADCAST_TRACK_TOPIC);
            ws.publish(BROADCAST_TRACK_TOPIC, {
                type: "connectionChange",
                userId: ws.data.user.id,
                state: "disconnected",
            });
        },
        auth: true,
        body: RealtimeModel.trackMessage,
        response: RealtimeModel.realtimeResponse,
    });
