import { t } from "elysia";

const trackMessage = t.Object({
    type: t.Literal("update"),
    userId: t.String({
        minLength: 1,
    }),
    latitude: t.Number(),
    longitude: t.Number(),
    state: t.Enum({
        free: "free",
        onTheWay: "onTheWay",
        occupied: "occupied",
        away: "away",
    }),
    fuelLevel: t.Optional(t.Number()),
});

const connectionChangeMessage = t.Object({
    type: t.Literal("connectionChange"),
    userId: t.String({
        minLength: 1,
    }),
    state: t.Enum({
        connected: "connected",
        disconnected: "disconnected",
    }),
});

const realtimeResponse = t.Union([trackMessage, connectionChangeMessage]);

export const RealtimeModel = {
    trackMessage,
    connectionChangeMessage,
    realtimeResponse,
} as const;
