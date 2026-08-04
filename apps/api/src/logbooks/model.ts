import { t } from "elysia";

export const LogbooksModel = {
    submitModel: t.Object({
        vehicleId: t.Optional(t.String({ format: "uuid" })),
        vehicleFingerprint: t.Optional(t.String()),
        startedAt: t.Date(),
        startOdometer: t.Integer(),
        endOdometer: t.Integer(),
        endedAt: t.Date(),
        revenue: t.Number(),
    }),
} as const;
