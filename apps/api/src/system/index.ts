import { Elysia } from "elysia";
import { authHandler } from "../authHandler";
import * as firmware from "./firmware";
import * as management from "./management";
import { SystemModel } from "./model";

const jsonRequest = (method: string, body?: unknown): RequestInit => ({
    method,
    headers:
        body === undefined ? undefined : { "content-type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
});

const firmwareResponse = async (operation: () => Promise<Response>) => {
    try {
        return await operation();
    } catch (error) {
        if (error instanceof Response) return error;

        console.error("Failed to stage firmware update", error);
        return Response.json(
            {
                error: {
                    code: "update_staging_failed",
                    message: "Could not stage the firmware update",
                },
            },
            { status: 500 },
        );
    }
};

export const system = new Elysia({
    prefix: "/system",
    tags: ["system"],
})
    .use(authHandler)
    .get(
        "/",
        async () => {
            const response = await management.request("/healthz", {
                signal: AbortSignal.timeout(3_000),
            });
            return {
                management: response.ok
                    ? { available: true as const }
                    : {
                          available: false as const,
                          reason: management.UNAVAILABLE_MESSAGE,
                      },
            };
        },
        {
            admin: true,
            detail: {
                summary: "Atlas OS management availability",
                description:
                    "Returns available=false when running outside Atlas OS.",
            },
        },
    )
    .get("/containers", () => management.request("/api/v1/containers"), {
        admin: true,
    })
    .get("/update", () => management.request("/api/v1/update"), {
        admin: true,
    })
    .post(
        "/update/upload",
        ({ request }) => firmwareResponse(() => firmware.fromUpload(request)),
        {
            admin: true,
            parse: "none",
            detail: {
                summary: "Install an uploaded Atlas OS update",
                description:
                    "Streams the raw update bundle to temporary disk storage.",
                requestBody: {
                    required: true,
                    content: {
                        "application/octet-stream": {
                            schema: { type: "string", format: "binary" },
                        },
                        "application/zstd": {
                            schema: { type: "string", format: "binary" },
                        },
                    },
                },
            },
        },
    )
    .post(
        "/update/url",
        ({ body }) => firmwareResponse(() => firmware.fromURL(body.url)),
        {
            admin: true,
            body: SystemModel.updateURL,
            detail: { summary: "Download and install an Atlas OS update" },
        },
    )
    .post(
        "/update/github",
        ({ body }) =>
            firmwareResponse(() => firmware.fromLatestGitHub(body.repository)),
        {
            admin: true,
            body: SystemModel.githubUpdate,
            detail: {
                summary: "Install the latest GitHub release update",
            },
        },
    )
    .post(
        "/update/rollback",
        () =>
            management.request("/api/v1/update/rollback", jsonRequest("POST")),
        { admin: true },
    )
    .post(
        "/power/reboot",
        () => management.request("/api/v1/power/reboot", jsonRequest("POST")),
        { admin: true },
    )
    .post(
        "/power/poweroff",
        () => management.request("/api/v1/power/poweroff", jsonRequest("POST")),
        { admin: true },
    )
    .post(
        "/factory-reset",
        () => management.request("/api/v1/factory-reset", jsonRequest("POST")),
        { admin: true },
    )
    .get("/ssh", () => management.request("/api/v1/ssh"), {
        admin: true,
    })
    .post(
        "/ssh/enable",
        () => management.request("/api/v1/ssh/enable", jsonRequest("POST")),
        { admin: true },
    )
    .post(
        "/ssh/disable",
        () => management.request("/api/v1/ssh/disable", jsonRequest("POST")),
        { admin: true },
    )
    .get("/timezone", () => management.request("/api/v1/timezone"), {
        admin: true,
    })
    .put(
        "/timezone",
        ({ body }) =>
            management.request("/api/v1/timezone", jsonRequest("PUT", body)),
        { admin: true, body: SystemModel.timezone },
    )
    .get(
        "/connections/adapters",
        () => management.request("/api/v1/connections/adapters"),
        { admin: true },
    )
    .get(
        "/connections/network-manager",
        () => management.request("/api/v1/connections/network-manager"),
        { admin: true },
    )
    .get(
        "/connections/network-manager/devices",
        () => management.request("/api/v1/connections/network-manager/devices"),
        { admin: true },
    )
    .get(
        "/connections/network-manager/wifi",
        ({ query }) => {
            const search = new URLSearchParams();
            if (query.device) search.set("device", query.device);
            const suffix = search.size === 0 ? "" : `?${search}`;
            return management.request(
                `/api/v1/connections/network-manager/wifi${suffix}`,
            );
        },
        {
            admin: true,
            query: SystemModel.wifiDevice,
        },
    )
    .post(
        "/connections/network-manager/wifi",
        ({ body }) =>
            management.request(
                "/api/v1/connections/network-manager/wifi",
                jsonRequest("POST", body),
            ),
        { admin: true, body: SystemModel.wifi },
    )
    .get(
        "/connections/network-manager/:uuid/ip",
        ({ params }) =>
            management.request(
                `/api/v1/connections/network-manager/${encodeURIComponent(params.uuid)}/ip`,
            ),
        { admin: true, params: SystemModel.uuid },
    )
    .put(
        "/connections/network-manager/:uuid/ip",
        ({ params, body }) =>
            management.request(
                `/api/v1/connections/network-manager/${encodeURIComponent(params.uuid)}/ip`,
                jsonRequest("PUT", body),
            ),
        {
            admin: true,
            params: SystemModel.uuid,
            body: SystemModel.ipSettings,
        },
    )
    .post(
        "/connections/network-manager/:uuid/disconnect",
        ({ params }) =>
            management.request(
                `/api/v1/connections/network-manager/${encodeURIComponent(params.uuid)}/disconnect`,
                jsonRequest("POST"),
            ),
        { admin: true, params: SystemModel.uuid },
    )
    .delete(
        "/connections/network-manager/:uuid",
        ({ params }) =>
            management.request(
                `/api/v1/connections/network-manager/${encodeURIComponent(params.uuid)}`,
                { method: "DELETE" },
            ),
        { admin: true, params: SystemModel.uuid },
    )
    .get(
        "/connections/auth-origins",
        () => management.request("/api/v1/connections/auth-origins"),
        { admin: true },
    )
    .post(
        "/connections/auth-origins",
        ({ body }) =>
            management.request(
                "/api/v1/connections/auth-origins",
                jsonRequest("POST", body),
            ),
        { admin: true, body: SystemModel.origin },
    )
    .delete(
        "/connections/auth-origins",
        ({ body }) =>
            management.request(
                "/api/v1/connections/auth-origins",
                jsonRequest("DELETE", body),
            ),
        { admin: true, body: SystemModel.origin },
    )
    .get(
        "/connections/remote-access",
        () => management.request("/api/v1/connections/remote-access"),
        { admin: true },
    )
    .put(
        "/connections/remote-access/cloudflare-tunnel",
        ({ body }) =>
            management.request(
                "/api/v1/connections/remote-access/cloudflare-tunnel",
                jsonRequest("PUT", body),
            ),
        { admin: true, body: SystemModel.cloudflareTunnel },
    )
    .delete(
        "/connections/remote-access/cloudflare-tunnel",
        () =>
            management.request(
                "/api/v1/connections/remote-access/cloudflare-tunnel",
                { method: "DELETE" },
            ),
        { admin: true },
    )
    .put(
        "/connections/remote-access/tailscale",
        ({ body }) =>
            management.request(
                "/api/v1/connections/remote-access/tailscale",
                jsonRequest("PUT", body),
            ),
        { admin: true, body: SystemModel.tailscale },
    )
    .delete(
        "/connections/remote-access/tailscale",
        () =>
            management.request("/api/v1/connections/remote-access/tailscale", {
                method: "DELETE",
            }),
        { admin: true },
    );
