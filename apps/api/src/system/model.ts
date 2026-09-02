import { t } from "elysia";

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

const httpsOrigin = t.String({
    format: "uri",
    pattern: "^https://[^/?#]+/?$",
    description: "HTTPS origin without credentials, a path, query, or fragment",
    examples: ["https://atlas.example.com"],
});

export const SystemModel = {
    timezone: t.Object({
        timezone: t.String({ minLength: 1, examples: ["Europe/Vienna"] }),
    }),
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
    origin: t.Object({
        origin: httpsOrigin,
    }),
    cloudflareTunnel: t.Object({
        token: t.String({ minLength: 1 }),
        origin: httpsOrigin,
    }),
    tailscale: t.Object({
        authKey: t.String({ minLength: 1 }),
        hostname: t.Optional(t.String()),
        origin: httpsOrigin,
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
    uuid: t.Object({
        uuid: t.String({ format: "uuid" }),
    }),
} as const;
