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

const routeLocation = t.Object({
    type: t.Union([
        t.Literal("break"),
        t.Literal("through"),
        t.Literal("via"),
        t.Literal("break_through"),
    ]),
    lat: t.Number({ minimum: -90, maximum: 90 }),
    lon: t.Number({ minimum: -180, maximum: 180 }),
    name: t.Optional(t.String()),
    street: t.Optional(t.String()),
    heading: t.Optional(t.Integer({ minimum: 0, maximum: 360 })),
    date_time: t.Optional(t.String()),
    time_zone_offset: t.Optional(t.String()),
    time_zone_name: t.Optional(t.String()),
    waiting: t.Optional(t.Integer({ minimum: 0 })),
    side_of_street: t.Optional(
        t.Union([t.Literal("left"), t.Literal("right")]),
    ),
    original_index: t.Integer({ minimum: 0 }),
});

const routeSignElement = t.Object({
    text: t.String(),
    consecutive_count: t.Optional(t.Integer({ minimum: 1 })),
});

const routeSign = t.Object({
    exit_number_elements: t.Optional(t.Array(routeSignElement)),
    exit_branch_elements: t.Optional(t.Array(routeSignElement)),
    exit_toward_elements: t.Optional(t.Array(routeSignElement)),
    exit_name_elements: t.Optional(t.Array(routeSignElement)),
});

const transitStop = t.Object({
    type: t.Union([t.Literal("stop"), t.Literal("station")]),
    onestop_id: t.Optional(t.String()),
    name: t.Optional(t.String()),
    arrival_date_time: t.Optional(t.String()),
    departure_date_time: t.Optional(t.String()),
    assumed_schedule: t.Boolean(),
    lat: t.Optional(t.Number({ minimum: -90, maximum: 90 })),
    lon: t.Optional(t.Number({ minimum: -180, maximum: 180 })),
});

const transitInfo = t.Object({
    onestop_id: t.Optional(t.String()),
    short_name: t.Optional(t.String()),
    long_name: t.Optional(t.String()),
    headsign: t.Optional(t.String()),
    color: t.Integer({ minimum: 0 }),
    text_color: t.Integer({ minimum: 0 }),
    description: t.Optional(t.String()),
    operator_onestop_id: t.Optional(t.String()),
    operator_name: t.Optional(t.String()),
    operator_url: t.Optional(t.String()),
    transit_stops: t.Optional(t.Array(transitStop)),
});

const routeManeuver = t.Object({
    type: t.Integer({ minimum: 0, maximum: 45 }),
    instruction: t.String(),
    verbal_transition_alert_instruction: t.Optional(t.String()),
    verbal_succinct_transition_instruction: t.Optional(t.String()),
    verbal_pre_transition_instruction: t.Optional(t.String()),
    verbal_post_transition_instruction: t.Optional(t.String()),
    street_names: t.Optional(t.Array(t.String())),
    begin_street_names: t.Optional(t.Array(t.String())),
    bearing_before: t.Optional(t.Integer({ minimum: 0, maximum: 360 })),
    bearing_after: t.Optional(t.Integer({ minimum: 0, maximum: 360 })),
    time: t.Number({ minimum: 0 }),
    length: t.Number({ minimum: 0 }),
    cost: t.Number({ minimum: 0 }),
    begin_shape_index: t.Integer({ minimum: 0 }),
    end_shape_index: t.Integer({ minimum: 0 }),
    toll: t.Optional(t.Boolean()),
    highway: t.Optional(t.Boolean()),
    ferry: t.Optional(t.Boolean()),
    rough: t.Optional(t.Boolean()),
    has_time_restrictions: t.Optional(t.Boolean()),
    sign: t.Optional(routeSign),
    roundabout_exit_count: t.Optional(t.Integer({ minimum: 1 })),
    depart_instruction: t.Optional(t.String()),
    verbal_depart_instruction: t.Optional(t.String()),
    arrive_instruction: t.Optional(t.String()),
    verbal_arrive_instruction: t.Optional(t.String()),
    transit_info: t.Optional(transitInfo),
    verbal_multi_cue: t.Optional(t.Boolean()),
    travel_mode: t.Union([
        t.Literal("drive"),
        t.Literal("pedestrian"),
        t.Literal("bicycle"),
        t.Literal("transit"),
    ]),
    travel_type: t.Union([
        t.Literal("car"),
        t.Literal("motorcycle"),
        t.Literal("bus"),
        t.Literal("truck"),
        t.Literal("motor_scooter"),
        t.Literal("foot"),
        t.Literal("wheelchair"),
        t.Literal("road"),
        t.Literal("cross"),
        t.Literal("hybrid"),
        t.Literal("mountain"),
        t.Literal("tram"),
        t.Literal("metro"),
        t.Literal("rail"),
        t.Literal("ferry"),
        t.Literal("cable_car"),
        t.Literal("gondola"),
        t.Literal("funicular"),
    ]),
    lanes: t.Optional(
        t.Array(
            t.Object({
                directions: t.Integer({ minimum: 0 }),
                active: t.Optional(t.Integer({ minimum: 0 })),
                valid: t.Optional(t.Integer({ minimum: 0 })),
            }),
        ),
    ),
});

const admin = t.Object({
    country_code: t.String(),
    country_text: t.String(),
    state_code: t.String(),
    state_text: t.String(),
});

const adminCrossing = t.Object({
    from_admin_index: t.Integer({ minimum: 0 }),
    to_admin_index: t.Integer({ minimum: 0 }),
    begin_shape_index: t.Integer({ minimum: 0 }),
    end_shape_index: t.Integer({ minimum: 0 }),
});

const routeSummaryProperties = {
    has_time_restrictions: t.Boolean(),
    has_toll: t.Boolean(),
    has_highway: t.Boolean(),
    has_ferry: t.Boolean(),
    min_lat: t.Number({ minimum: -90, maximum: 90 }),
    min_lon: t.Number({ minimum: -180, maximum: 180 }),
    max_lat: t.Number({ minimum: -90, maximum: 90 }),
    max_lon: t.Number({ minimum: -180, maximum: 180 }),
    time: t.Number({ minimum: 0 }),
    length: t.Number({ minimum: 0 }),
    cost: t.Number({ minimum: 0 }),
} as const;

const routeSummary = t.Object(routeSummaryProperties);

const routeLeg = t.Object({
    maneuvers: t.Optional(t.Array(routeManeuver)),
    elevation_interval: t.Optional(t.Number({ exclusiveMinimum: 0 })),
    elevation: t.Optional(t.Array(t.Number())),
    summary: t.Object({
        ...routeSummaryProperties,
        admins: t.Optional(t.Array(admin)),
        admin_crossings: t.Optional(t.Array(adminCrossing)),
        level_changes: t.Optional(
            t.Array(t.Tuple([t.Integer({ minimum: 0 }), t.Number()])),
        ),
    }),
    shape: t.String(),
});

const routeWarning = t.Object({
    code: t.Integer(),
    text: t.String(),
});

const trip = t.Object({
    locations: t.Array(routeLocation),
    legs: t.Array(routeLeg),
    linear_references: t.Optional(t.Array(t.String())),
    summary: routeSummary,
    status_message: t.String(),
    status: t.Literal(0),
    units: t.Union([t.Literal("kilometers"), t.Literal("miles")]),
    language: t.String(),
    warnings: t.Optional(t.Array(routeWarning)),
});

const routeSuccessResponse = t.Object({
    trip,
    alternates: t.Optional(t.Array(t.Object({ trip }))),
    id: t.Optional(t.String()),
});

const routeErrorResponse = t.Object({
    error_code: t.Integer(),
    error: t.String(),
    status_code: t.Integer({ minimum: 400, maximum: 599 }),
    status: t.String(),
});

export const GeoservicesModel = {
    resolveQuery: t.Object({
        address: t.String({
            minLength: 1,
        }),
    }),
    geocoderResponse: t.Union([geocoderSuccessResponse, geocoderErrorResponse]),
    routeQuery: t.Object({
        fromlat: t.Number({ minimum: -90, maximum: 90 }),
        fromlon: t.Number({ minimum: -180, maximum: 180 }),
        tolat: t.Number({ minimum: -90, maximum: 90 }),
        tolon: t.Number({ minimum: -180, maximum: 180 }),
        lang: t.Optional(t.String({ minLength: 2, maxLength: 5 })),
    }),
    routeResponse: t.Union([routeSuccessResponse, routeErrorResponse]),
} as const;

export type GeocoderResponse = Static<typeof GeoservicesModel.geocoderResponse>;
