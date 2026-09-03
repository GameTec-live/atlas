import type { TSchema } from "@sinclair/typebox";
import { t } from "elysia";

const list = <Item extends TSchema>(item: Item) =>
    t.Object({
        items: t.Array(item),
        count: t.Integer({ minimum: 0 }),
    });

const ipFamily = t.Object({
    method: t.Union([
        t.Literal("auto"),
        t.Literal("manual"),
        t.Literal("disabled"),
    ]),
    addresses: t.Array(t.String()),
    gateway: t.Optional(t.String()),
    dns: t.Array(t.String()),
});

const ipFamilyResponse = t.Object({
    method: t.String(),
    addresses: t.Array(t.String()),
    gateway: t.Optional(t.String()),
    dns: t.Array(t.String()),
});

const httpsOrigin = t.String({
    format: "uri",
    pattern: "^https://[^/?#]+/?$",
    description: "HTTPS origin without credentials, a path, query, or fragment",
    examples: ["https://atlas.example.com"],
});

const error = t.Object({
    error: t.Object({
        code: t.String(),
        message: t.String(),
    }),
});

const ok = t.Object({ status: t.Literal("ok") });

const remoteAccess = t.Object({
    cloudflareTunnel: t.Object({
        provisioned: t.Boolean(),
        state: t.String(),
        detail: t.Optional(t.String()),
    }),
    tailscale: t.Object({
        provisioned: t.Boolean(),
        state: t.String(),
        detail: t.Optional(t.String()),
    }),
});

export const SystemModel = {
    error,
    availability: t.Object({
        management: t.Union([
            t.Object({ available: t.Literal(true) }),
            t.Object({
                available: t.Literal(false),
                reason: t.String(),
            }),
        ]),
    }),
    containers: list(
        t.Object({
            name: t.String(),
            image: t.String(),
            imageId: t.String(),
            version: t.String(),
        }),
    ),
    updateStatus: t.Object({
        update: t.Object({
            active: t.String(),
            other: t.String(),
            pending: t.Optional(t.String()),
        }),
        monitor: t.Object({
            phase: t.String(),
            healthySince: t.Optional(t.String({ format: "date-time" })),
            detail: t.Optional(t.String()),
        }),
    }),
    updateAccepted: t.Object({
        status: t.Literal("rebooting_into_candidate"),
    }),
    uploadStart: t.Object({
        uploadId: t.String({ format: "uuid" }),
        chunkSize: t.Integer({ minimum: 1 }),
    }),
    uploadProgress: t.Object({
        received: t.Integer({ minimum: 1 }),
    }),
    uploadInstalling: t.Object({ status: t.Literal("installing") }),
    ok,
    rebootAccepted: t.Object({ status: t.Literal("rebooting") }),
    poweroffAccepted: t.Object({ status: t.Literal("powering_off") }),
    factoryResetAccepted: t.Object({
        status: t.Literal("factory_reset_scheduled"),
    }),
    ssh: t.Object({ enabled: t.Boolean() }),
    timezone: t.Object({
        timezone: t.String({ minLength: 1, examples: ["Europe/Vienna"] }),
    }),
    adapters: t.Object({
        items: t.Array(
            t.Object({
                id: t.String(),
                status: t.String(),
            }),
        ),
    }),
    connections: list(
        t.Object({
            uuid: t.String(),
            name: t.String(),
            type: t.String(),
            device: t.Optional(t.String()),
        }),
    ),
    devices: list(
        t.Object({
            interface: t.String(),
            type: t.String(),
            state: t.String(),
            connection: t.Optional(t.String()),
        }),
    ),
    accessPoints: list(
        t.Object({
            active: t.Boolean(),
            ssid: t.String(),
            bssid: t.String(),
            signal: t.Integer(),
            security: t.Optional(t.String()),
            frequency: t.Integer(),
        }),
    ),
    wifi: t.Object({
        ssid: t.String({ minLength: 1 }),
        password: t.Optional(t.String()),
        device: t.Optional(t.String()),
        hidden: t.Optional(t.Boolean()),
    }),
    wifiDevice: t.Object({
        device: t.Optional(t.String()),
    }),
    ipSettings: t.Object({
        ipv4: ipFamily,
        ipv6: ipFamily,
    }),
    ipSettingsResponse: t.Object({
        ipv4: ipFamilyResponse,
        ipv6: ipFamilyResponse,
    }),
    origin: t.Object({
        origin: httpsOrigin,
    }),
    origins: list(t.String()),
    remoteAccess,
    cloudflareTunnel: t.Object({
        token: t.String({ minLength: 1 }),
        origin: t.Optional(httpsOrigin),
    }),
    tailscale: t.Object({
        authKey: t.String({ minLength: 1 }),
        hostname: t.Optional(t.String()),
        origin: t.Optional(httpsOrigin),
    }),
    updateURL: t.Object({
        url: t.String({ format: "uri" }),
    }),
    githubUpdate: t.Object({
        repository: t.Optional(
            t.String({
                pattern: "^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$",
                default: "GameTec-live/atlas",
                examples: ["GameTec-live/atlas"],
            }),
        ),
    }),
    uploadSize: t.Object({
        size: t.Integer({ minimum: 1 }),
    }),
    uploadId: t.Object({
        uploadId: t.String({ format: "uuid" }),
    }),
    uuid: t.Object({
        uuid: t.String({ format: "uuid" }),
    }),
} as const;
