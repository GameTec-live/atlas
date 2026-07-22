import type { Static } from "@sinclair/typebox";
import { t } from "elysia";

const trackMessage = t.Object({
    type: t.Literal("update"),
    userId: t.String({
        minLength: 1,
    }),
    userName: t.String({
        minLength: 1,
    }),
    latitude: t.Number({ minimum: -90, maximum: 90 }),
    longitude: t.Number({ minimum: -180, maximum: 180 }),
    state: t.Enum({
        free: "free",
        onTheWay: "onTheWay",
        occupied: "occupied",
        away: "away",
    }),
    fuelLevel: t.Optional(t.Number()),
});

const trackInputMessage = t.Omit(trackMessage, ["userId", "userName"]);

const connectionChangeMessage = t.Object({
    type: t.Literal("connectionChange"),
    userId: t.String({
        minLength: 1,
    }),
    userName: t.String({
        minLength: 1,
    }),
    state: t.Enum({
        connected: "connected",
        disconnected: "disconnected",
    }),
});

const realtimeResponse = t.Union([trackMessage, connectionChangeMessage]);

export const RealtimeModel = {
    realtimeResponse,
    trackInputMessage,
    notifyResponse: t.Object({
        jobId: t.String({ format: "uuid" }),
        from: t.String(),
        to: t.Optional(t.String()),
        note: t.Optional(t.String()),
    }),
} as const;

export type NotifyResponse = Static<typeof RealtimeModel.notifyResponse>;
