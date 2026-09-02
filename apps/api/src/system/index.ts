import { Elysia } from "elysia";
import { authHandler } from "../authHandler";
import { getDeploymentCapabilities } from "../capabilities";
import * as firmware from "./firmware";
import * as management from "./management";
import { SystemModel } from "./model";
import { forwardJSON } from "./proxy";
import { SystemResponse } from "./responses";

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
            if (!getDeploymentCapabilities().systemManagement) {
                return {
                    management: {
                        available: false as const,
                        reason: management.UNAVAILABLE_MESSAGE,
                    },
                };
            }

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
            response: SystemResponse.availability,
            detail: {
                summary: "Atlas OS management availability",
                description:
                    "Returns available=false when running outside Atlas OS.",
            },
        },
    )
    .get("/containers", () => management.request("/api/v1/containers"), {
        admin: true,
        response: SystemResponse.containers,
    })
    .get("/update", () => management.request("/api/v1/update"), {
        admin: true,
        response: SystemResponse.updateStatus,
    })
    .post(
        "/update/upload",
        ({ request, status, set }) =>
            forwardJSON(
                firmwareResponse(() => firmware.fromUpload(request)),
                { status, set },
            ),
        {
            admin: true,
            parse: "none",
            response: SystemResponse.updateUpload,
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
        ({ body, status, set }) =>
            forwardJSON(
                firmwareResponse(() => firmware.fromURL(body.url)),
                { status, set },
            ),
        {
            admin: true,
            body: SystemModel.updateURL,
            response: SystemResponse.updateDownload,
            detail: { summary: "Download and install an Atlas OS update" },
        },
    )
    .post(
        "/update/github",
        ({ body, status, set }) =>
            forwardJSON(
                firmwareResponse(() =>
                    firmware.fromLatestGitHub(body.repository),
                ),
                { status, set },
            ),
        {
            admin: true,
            body: SystemModel.githubUpdate,
            response: SystemResponse.updateDownload,
            detail: {
                summary: "Install the latest GitHub release update",
            },
        },
    )
    .post(
        "/update/rollback",
        () =>
            management.request("/api/v1/update/rollback", jsonRequest("POST")),
        {
            admin: true,
            response: SystemResponse.mutation,
        },
    )
    .post(
        "/power/reboot",
        ({ status, set }) =>
            forwardJSON(
                management.request("/api/v1/power/reboot", jsonRequest("POST")),
                { status, set },
            ),
        {
            admin: true,
            response: SystemResponse.reboot,
        },
    )
    .post(
        "/power/poweroff",
        ({ status, set }) =>
            forwardJSON(
                management.request(
                    "/api/v1/power/poweroff",
                    jsonRequest("POST"),
                ),
                { status, set },
            ),
        {
            admin: true,
            response: SystemResponse.poweroff,
        },
    )
    .post(
        "/factory-reset",
        ({ status, set }) =>
            forwardJSON(
                management.request(
                    "/api/v1/factory-reset",
                    jsonRequest("POST"),
                ),
                { status, set },
            ),
        {
            admin: true,
            response: SystemResponse.factoryReset,
        },
    )
    .get("/ssh", () => management.request("/api/v1/ssh"), {
        admin: true,
        response: SystemResponse.ssh,
    })
    .post(
        "/ssh/enable",
        () => management.request("/api/v1/ssh/enable", jsonRequest("POST")),
        {
            admin: true,
            response: SystemResponse.mutation,
        },
    )
    .post(
        "/ssh/disable",
        () => management.request("/api/v1/ssh/disable", jsonRequest("POST")),
        {
            admin: true,
            response: SystemResponse.mutation,
        },
    )
    .get("/timezone", () => management.request("/api/v1/timezone"), {
        admin: true,
        response: SystemResponse.timezone,
    })
    .put(
        "/timezone",
        ({ body }) =>
            management.request("/api/v1/timezone", jsonRequest("PUT", body)),
        {
            admin: true,
            body: SystemModel.timezone,
            response: SystemResponse.timezoneUpdate,
        },
    )
    .get(
        "/connections/adapters",
        () => management.request("/api/v1/connections/adapters"),
        {
            admin: true,
            response: SystemResponse.adapters,
        },
    )
    .get(
        "/connections/network-manager",
        () => management.request("/api/v1/connections/network-manager"),
        {
            admin: true,
            response: SystemResponse.connections,
        },
    )
    .get(
        "/connections/network-manager/devices",
        () => management.request("/api/v1/connections/network-manager/devices"),
        {
            admin: true,
            response: SystemResponse.devices,
        },
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
            response: SystemResponse.accessPoints,
        },
    )
    .post(
        "/connections/network-manager/wifi",
        ({ body }) =>
            management.request(
                "/api/v1/connections/network-manager/wifi",
                jsonRequest("POST", body),
            ),
        {
            admin: true,
            body: SystemModel.wifi,
            response: SystemResponse.mutation,
        },
    )
    .get(
        "/connections/network-manager/:uuid/ip",
        ({ params }) =>
            management.request(
                `/api/v1/connections/network-manager/${encodeURIComponent(params.uuid)}/ip`,
            ),
        {
            admin: true,
            params: SystemModel.uuid,
            response: SystemResponse.ipSettings,
        },
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
            response: SystemResponse.mutation,
        },
    )
    .post(
        "/connections/network-manager/:uuid/disconnect",
        ({ params }) =>
            management.request(
                `/api/v1/connections/network-manager/${encodeURIComponent(params.uuid)}/disconnect`,
                jsonRequest("POST"),
            ),
        {
            admin: true,
            params: SystemModel.uuid,
            response: SystemResponse.mutation,
        },
    )
    .delete(
        "/connections/network-manager/:uuid",
        ({ params }) =>
            management.request(
                `/api/v1/connections/network-manager/${encodeURIComponent(params.uuid)}`,
                { method: "DELETE" },
            ),
        {
            admin: true,
            params: SystemModel.uuid,
            response: SystemResponse.mutation,
        },
    )
    .get(
        "/connections/auth-origins",
        () => management.request("/api/v1/connections/auth-origins"),
        {
            admin: true,
            response: SystemResponse.origins,
        },
    )
    .post(
        "/connections/auth-origins",
        ({ body }) =>
            management.request(
                "/api/v1/connections/auth-origins",
                jsonRequest("POST", body),
            ),
        {
            admin: true,
            body: SystemModel.origin,
            response: SystemResponse.originsMutation,
        },
    )
    .delete(
        "/connections/auth-origins",
        ({ body }) =>
            management.request(
                "/api/v1/connections/auth-origins",
                jsonRequest("DELETE", body),
            ),
        {
            admin: true,
            body: SystemModel.origin,
            response: SystemResponse.originsMutation,
        },
    )
    .get(
        "/connections/remote-access",
        () => management.request("/api/v1/connections/remote-access"),
        {
            admin: true,
            response: SystemResponse.remoteAccess,
        },
    )
    .put(
        "/connections/remote-access/cloudflare-tunnel",
        ({ body }) =>
            management.request(
                "/api/v1/connections/remote-access/cloudflare-tunnel",
                jsonRequest("PUT", body),
            ),
        {
            admin: true,
            body: SystemModel.cloudflareTunnel,
            response: SystemResponse.remoteAccessMutation,
        },
    )
    .delete(
        "/connections/remote-access/cloudflare-tunnel",
        () =>
            management.request(
                "/api/v1/connections/remote-access/cloudflare-tunnel",
                { method: "DELETE" },
            ),
        {
            admin: true,
            response: SystemResponse.remoteAccessMutation,
        },
    )
    .put(
        "/connections/remote-access/tailscale",
        ({ body }) =>
            management.request(
                "/api/v1/connections/remote-access/tailscale",
                jsonRequest("PUT", body),
            ),
        {
            admin: true,
            body: SystemModel.tailscale,
            response: SystemResponse.remoteAccessMutation,
        },
    )
    .delete(
        "/connections/remote-access/tailscale",
        () =>
            management.request("/api/v1/connections/remote-access/tailscale", {
                method: "DELETE",
            }),
        {
            admin: true,
            response: SystemResponse.remoteAccessMutation,
        },
    );
