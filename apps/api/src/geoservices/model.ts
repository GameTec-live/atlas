import type { Static } from "@sinclair/typebox";
import { t } from "elysia";

const geocoderResult = t.Object({
    pack: t.String(),
    source: t.Optional(t.String()),
    source_id: t.Optional(t.String()),
    kind: t.String(),
    name: t.Optional(t.String()),
    house_number: t.Optional(t.String()),
    street: t.Optional(t.String()),
    unit: t.Optional(t.String()),
    postcode: t.Optional(t.String()),
    locality: t.Optional(t.String()),
    district: t.Optional(t.String()),
    region: t.Optional(t.String()),
    country_code: t.Optional(t.String()),
    country: t.Optional(t.String()),
    lat: t.Number({ minimum: -90, maximum: 90 }),
    lon: t.Number({ minimum: -180, maximum: 180 }),
    importance: t.Optional(t.Number()),
    aliases: t.Optional(t.Array(t.String())),
    display_name: t.String(),
    score: t.Optional(t.Number()),
    distance_m: t.Optional(t.Number({ minimum: 0 })),
});

const geocoderSuccessResponse = t.Object({
    query: t.String(),
    count: t.Integer({ minimum: 0 }),
    results: t.Array(geocoderResult),
});

const geocoderErrorResponse = t.Object({
    error: t.Object({
        code: t.String(),
        message: t.String(),
    }),
});

export const GeoservicesModel = {
    resolveQuery: t.Object({
        address: t.String({
            minLength: 1,
        }),
    }),
    geocoderResponse: t.Union([geocoderSuccessResponse, geocoderErrorResponse]),
} as const;

export type GeocoderResponse = Static<typeof GeoservicesModel.geocoderResponse>;
