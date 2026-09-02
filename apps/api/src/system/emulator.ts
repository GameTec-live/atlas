import { randomUUID } from "node:crypto";
import { Elysia } from "elysia";
import { SystemModel } from "./model";

const emulatorEnv = Bun.env as { OS_MANAGEMENT_EMULATOR_TOKEN?: string };
export const EMULATOR_TOKEN =
    emulatorEnv.OS_MANAGEMENT_EMULATOR_TOKEN ?? "atlas-management-emulator";

const emptyIPSettings = () => ({
    ipv4: { method: "auto", addresses: [] as string[], dns: [] as string[] },
    ipv6: { method: "auto", addresses: [] as string[], dns: [] as string[] },
});

const ethernetUUID = "00000000-0000-4000-8000-000000000001";
const state = {
    update: {
        active: "system_a",
        other: "system_b",
        pending: undefined as string | undefined,
    },
    monitor: {
        phase: "idle",
        healthySince: undefined as string | undefined,
    },
    ssh: false,
    timezone: "Etc/UTC",
    connections: [
        {
            uuid: ethernetUUID,
            name: "Wired connection 1",
            type: "802-3-ethernet",
            device: "eth0",
        },
    ],
    devices: [
        {
            interface: "eth0",
            type: "ethernet",
            state: "connected",
            connection: "Wired connection 1",
        },
        {
            interface: "wlan0",
            type: "wifi",
            state: "disconnected",
            connection: "",
        },
    ],
    ipSettings: new Map([[ethernetUUID, emptyIPSettings()]]),
    origins: new Set<string>(),
    remoteAccess: {
        cloudflareTunnel: {
            provisioned: false,
            state: "not_provisioned",
        },
        tailscale: { provisioned: false, state: "not_provisioned" },
    },
};

const ok = () => ({ status: "ok" });
const list = <Item>(items: Item[]) => ({ items, count: items.length });

export const managementEmulator = new Elysia()
    .onBeforeHandle(({ request, status }) => {
        if (
            request.headers.get("authorization") !== `Bearer ${EMULATOR_TOKEN}`
        ) {
            return status(401, {
                error: {
                    code: "unauthorized",
                    message: "a valid bearer token is required",
                },
            });
        }
        return undefined;
    })
    .get("/healthz", () => ok())
    .get("/api/v1/update", () => ({
        update: state.update,
        monitor: state.monitor,
    }))
    .post(
        "/api/v1/update",
        async ({ request, status }) => {
            if (state.update.pending) {
                return status(409, {
                    error: {
                        code: "operation_in_progress",
                        message: "an update is already pending",
                    },
                });
            }

            const bundle = (await request.formData()).get("bundle");
            if (!(bundle instanceof File) || bundle.size === 0) {
                return status(400, {
                    error: {
                        code: "invalid_update",
                        message:
                            "multipart request must contain one bundle file",
                    },
                });
            }

            const previous = state.update.active;
            state.update.active = state.update.other;
            state.update.other = previous;
            state.update.pending = state.update.active;
            state.monitor.phase = "monitoring";
            state.monitor.healthySince = new Date().toISOString();
            return status(202, { status: "rebooting_into_candidate" });
        },
        { parse: "none" },
    )
    .post("/api/v1/update/rollback", () => {
        if (state.update.pending === state.update.active) {
            const candidate = state.update.active;
            state.update.active = state.update.other;
            state.update.other = candidate;
        }
        state.update.pending = undefined;
        state.monitor = { phase: "idle", healthySince: undefined };
        return ok();
    })
    .get("/api/v1/containers", () =>
        list(
            [
                "web",
                "api",
                "db",
                "map",
                "router",
                "geocoder",
                "geodata-api",
                "geodata-reloader",
            ].map((name) => ({
                name: `atlas-${name}`,
                image: `ghcr.io/gametec-live/atlas-${name}:latest`,
                imageId: `sha256:emulated-${name}`,
                version: "emulator",
            })),
        ),
    )
    .post("/api/v1/power/reboot", ({ status }) =>
        status(202, { status: "rebooting" }),
    )
    .post("/api/v1/power/poweroff", ({ status }) =>
        status(202, { status: "powering_off" }),
    )
    .post("/api/v1/factory-reset", ({ status }) => {
        state.ssh = false;
        state.timezone = "Etc/UTC";
        state.origins.clear();
        state.remoteAccess.cloudflareTunnel = {
            provisioned: false,
            state: "not_provisioned",
        };
        state.remoteAccess.tailscale = {
            provisioned: false,
            state: "not_provisioned",
        };
        return status(202, { status: "factory_reset_scheduled" });
    })
    .get("/api/v1/ssh", () => ({ enabled: state.ssh }))
    .post("/api/v1/ssh/enable", () => {
        state.ssh = true;
        return ok();
    })
    .post("/api/v1/ssh/disable", () => {
        state.ssh = false;
        return ok();
    })
    .get("/api/v1/timezone", () => ({ timezone: state.timezone }))
    .put(
        "/api/v1/timezone",
        ({ body }) => {
            state.timezone = body.timezone;
            return body;
        },
        { body: SystemModel.timezone },
    )
    .get("/api/v1/connections/adapters", () =>
        list(
            [
                "network-manager",
                "auth-origins",
                "cloudflare-tunnel",
                "tailscale",
            ].map((id) => ({ id, status: "available" })),
        ),
    )
    .get("/api/v1/connections/network-manager", () => list(state.connections))
    .get("/api/v1/connections/network-manager/devices", () =>
        list(state.devices),
    )
    .get("/api/v1/connections/network-manager/wifi", () =>
        list([
            {
                active: false,
                ssid: "Atlas Emulator WiFi",
                bssid: "02:00:00:00:00:01",
                signal: 90,
                security: "WPA2",
                frequency: 5180,
            },
            {
                active: false,
                ssid: "Guest",
                bssid: "02:00:00:00:00:02",
                signal: 65,
                security: "",
                frequency: 2412,
            },
        ]),
    )
    .post(
        "/api/v1/connections/network-manager/wifi",
        ({ body }) => {
            const uuid = randomUUID();
            state.connections.push({
                uuid,
                name: body.ssid,
                type: "802-11-wireless",
                device: body.device ?? "wlan0",
            });
            state.ipSettings.set(uuid, emptyIPSettings());
            const wifi = state.devices.find(
                (device) => device.interface === (body.device ?? "wlan0"),
            );
            if (wifi) {
                wifi.state = "connected";
                wifi.connection = body.ssid;
            }
            return ok();
        },
        {
            body: SystemModel.wifi,
        },
    )
    .get(
        "/api/v1/connections/network-manager/:uuid/ip",
        ({ params, status }) => {
            const settings = state.ipSettings.get(params.uuid);
            return settings
                ? structuredClone(settings)
                : status(400, {
                      error: {
                          code: "ip_settings_failed",
                          message: "connection not found",
                      },
                  });
        },
    )
    .put(
        "/api/v1/connections/network-manager/:uuid/ip",
        ({ params, body, status }) => {
            if (!state.ipSettings.has(params.uuid)) {
                return status(400, {
                    error: {
                        code: "ip_settings_failed",
                        message: "connection not found",
                    },
                });
            }
            state.ipSettings.set(params.uuid, structuredClone(body));
            return ok();
        },
        { body: SystemModel.ipSettings },
    )
    .post(
        "/api/v1/connections/network-manager/:uuid/disconnect",
        ({ params, status }) => {
            const connection = state.connections.find(
                ({ uuid }) => uuid === params.uuid,
            );
            if (!connection) {
                return status(400, {
                    error: {
                        code: "disconnect_failed",
                        message: "connection not found",
                    },
                });
            }
            connection.device = "";
            return ok();
        },
    )
    .delete(
        "/api/v1/connections/network-manager/:uuid",
        ({ params, status }) => {
            const index = state.connections.findIndex(
                ({ uuid }) => uuid === params.uuid,
            );
            if (index === -1) {
                return status(400, {
                    error: {
                        code: "forget_failed",
                        message: "connection not found",
                    },
                });
            }
            state.connections.splice(index, 1);
            state.ipSettings.delete(params.uuid);
            return ok();
        },
    )
    .get("/api/v1/connections/auth-origins", () =>
        list([...state.origins].sort()),
    )
    .post(
        "/api/v1/connections/auth-origins",
        ({ body }) => {
            state.origins.add(body.origin);
            return list([...state.origins].sort());
        },
        { body: SystemModel.origin },
    )
    .delete(
        "/api/v1/connections/auth-origins",
        ({ body }) => {
            state.origins.delete(body.origin);
            return list([...state.origins].sort());
        },
        { body: SystemModel.origin },
    )
    .get("/api/v1/connections/remote-access", () => state.remoteAccess)
    .put(
        "/api/v1/connections/remote-access/cloudflare-tunnel",
        ({ body }) => {
            if (body.origin) state.origins.add(body.origin);
            state.remoteAccess.cloudflareTunnel = {
                provisioned: true,
                state: "active",
            };
            return state.remoteAccess;
        },
        { body: SystemModel.cloudflareTunnel },
    )
    .delete("/api/v1/connections/remote-access/cloudflare-tunnel", () => {
        state.remoteAccess.cloudflareTunnel = {
            provisioned: false,
            state: "not_provisioned",
        };
        return state.remoteAccess;
    })
    .put(
        "/api/v1/connections/remote-access/tailscale",
        ({ body }) => {
            if (body.origin) state.origins.add(body.origin);
            state.remoteAccess.tailscale = {
                provisioned: true,
                state: "active",
            };
            return state.remoteAccess;
        },
        { body: SystemModel.tailscale },
    )
    .delete("/api/v1/connections/remote-access/tailscale", () => {
        state.remoteAccess.tailscale = {
            provisioned: false,
            state: "not_provisioned",
        };
        return state.remoteAccess;
    });
