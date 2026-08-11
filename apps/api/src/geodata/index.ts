import type { Static, TSchema } from "@sinclair/typebox";
import { Value } from "@sinclair/typebox/value";
import { Elysia } from "elysia";
import { env } from "@/env";
import { authHandler } from "../authHandler";
import {
    GeodataModel,
    type InstallBody,
    type UpstreamCatalogResponse,
    type UpstreamDataset,
    type UpstreamJob,
    type UpstreamJobResponse,
    type UpstreamJobUpdate,
} from "./model";

export const GEODATA_TIMEOUT_MS = 30_000;

const GEODATA_URL = env.GEODATA_URL.replace(/\/+$/, "");
const GEODATA_WEBSOCKET_URL = `${GEODATA_URL.replace(/^http/, "ws")}/api/v1/jobs/ws`;
const websocketConnections = new WeakMap<object, WebSocket>();

async function requestGeodata<T extends TSchema>(
    path: string,
    schema: T,
    init?: RequestInit,
): Promise<{ status: number; result: Static<T> }> {
    const headers = new Headers(init?.headers);
    headers.set("accept", "application/json");

    const response = await fetch(`${GEODATA_URL}/api/v1${path}`, {
        ...init,
        headers,
        signal: init?.signal ?? AbortSignal.timeout(GEODATA_TIMEOUT_MS),
    });

    return {
        status: response.status,
        result: Value.Decode(schema, await response.json()),
    };
}

function installRequest(body: InstallBody): RequestInit {
    const payload =
        "name" in body
            ? { name: body.name }
            : {
                  id: body.id,
                  bbox: {
                      minLongitude: body.minLongitude,
                      minLatitude: body.minLatitude,
                      maxLongitude: body.maxLongitude,
                      maxLatitude: body.maxLatitude,
                  },
              };
    return {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(payload),
    };
}

function simplifyCatalog(response: UpstreamCatalogResponse) {
    if ("error" in response) return response;
    return {
        items: response.items.map(({ id, name, parent, bounds }) => ({
            id,
            name,
            parent,
            bounds,
        })),
        count: response.count,
    };
}

function simplifyDataset(dataset: UpstreamDataset) {
    const size = (kind: "pbf" | "geocoder" | "map") =>
        dataset.artifacts.find((artifact) => artifact.kind === kind)
            ?.size_bytes;
    return {
        id: dataset.id,
        name: dataset.name,
        state: dataset.state,
        source_type: dataset.source_type,
        bounds: dataset.bounds,
        size_bytes: {
            pbf: size("pbf"),
            geocoder: size("geocoder"),
            map: size("map"),
        },
        installed_at: dataset.installed_at,
    };
}

function simplifyJob({
    bytes_done: _bytesDone,
    bytes_total: _bytesTotal,
    request: _request,
    ...job
}: UpstreamJob) {
    return job;
}

function simplifyJobResponse(response: UpstreamJobResponse) {
    return "error" in response ? response : simplifyJob(response);
}

function simplifyJobUpdate(update: UpstreamJobUpdate) {
    return update.type === "snapshot"
        ? { type: update.type, jobs: update.jobs.map(simplifyJob) }
        : { type: update.type, job: simplifyJob(update.job) };
}

export const geodata = new Elysia({
    prefix: "/geodata",
    tags: ["geodata"],
    websocket: {
        idleTimeout: 60 * 5,
    },
})
    .use(authHandler)
    .get(
        "/catalog",
        async ({ query, set }) => {
            const params = new URLSearchParams();
            if (query.q !== undefined) params.set("q", query.q);
            if (query.parent !== undefined) {
                params.set("parent", query.parent);
            }

            const suffix = params.size > 0 ? `?${params}` : "";
            const { status, result } = await requestGeodata(
                `/catalog${suffix}`,
                GeodataModel.upstream.catalogResponse,
            );
            set.status = status;
            return simplifyCatalog(result);
        },
        {
            admin: true,
            query: GeodataModel.catalogQuery,
            response: GeodataModel.catalogResponse,
        },
    )
    .get(
        "/datasets",
        async ({ set }) => {
            const { status, result } = await requestGeodata(
                "/datasets",
                GeodataModel.upstream.datasetsResponse,
            );
            set.status = status;
            return {
                items: result.items.map(simplifyDataset),
                count: result.count,
            };
        },
        {
            admin: true,
            response: GeodataModel.datasetsResponse,
        },
    )
    .post(
        "/datasets",
        async ({ body, set }) => {
            const { status, result } = await requestGeodata(
                "/datasets",
                GeodataModel.upstream.jobResponse,
                installRequest(body),
            );
            set.status = status;
            return simplifyJobResponse(result);
        },
        {
            admin: true,
            body: GeodataModel.installBody,
            response: GeodataModel.jobResponse,
        },
    )
    .get(
        "/jobs",
        async ({ query, set }) => {
            const suffix =
                query.active === undefined ? "" : `?active=${query.active}`;
            const { status, result } = await requestGeodata(
                `/jobs${suffix}`,
                GeodataModel.upstream.jobsResponse,
            );
            set.status = status;
            return {
                items: result.items.map(simplifyJob),
                count: result.count,
            };
        },
        {
            admin: true,
            query: GeodataModel.jobsQuery,
            response: GeodataModel.jobsResponse,
        },
    )
    .ws("/jobs/live/ws", {
        open(ws) {
            const upstream = new WebSocket(GEODATA_WEBSOCKET_URL);
            websocketConnections.set(ws.raw, upstream);

            upstream.addEventListener("message", (event) => {
                if (typeof event.data !== "string") {
                    ws.close(1011, "Invalid geodata update");
                    return;
                }

                try {
                    ws.send(
                        simplifyJobUpdate(
                            Value.Decode(
                                GeodataModel.upstream.jobUpdate,
                                JSON.parse(event.data),
                            ),
                        ),
                    );
                } catch {
                    ws.close(1011, "Invalid geodata update");
                }
            });
            upstream.addEventListener("error", () => {
                ws.close(1011, "Geodata service unavailable");
            });
            upstream.addEventListener("close", (event) => {
                websocketConnections.delete(ws.raw);
                ws.close(event.code || 1000, event.reason);
            });
        },
        close(ws) {
            const upstream = websocketConnections.get(ws.raw);
            websocketConnections.delete(ws.raw);
            if (upstream && upstream.readyState < WebSocket.CLOSING) {
                upstream.close();
            }
        },
        admin: true,
        response: GeodataModel.jobUpdate,
    })
    .get(
        "/jobs/:id",
        async ({ params, set }) => {
            const { status, result } = await requestGeodata(
                `/jobs/${encodeURIComponent(params.id)}`,
                GeodataModel.upstream.jobResponse,
            );
            set.status = status;
            return simplifyJobResponse(result);
        },
        {
            admin: true,
            response: GeodataModel.jobResponse,
        },
    )
    .delete(
        "/jobs/:id",
        async ({ params, set }) => {
            const { status, result } = await requestGeodata(
                `/jobs/${encodeURIComponent(params.id)}`,
                GeodataModel.upstream.jobResponse,
                { method: "DELETE" },
            );
            set.status = status;
            return simplifyJobResponse(result);
        },
        {
            admin: true,
            response: GeodataModel.jobResponse,
        },
    )
    .delete(
        "/datasets/:id",
        async ({ params, set }) => {
            const { status, result } = await requestGeodata(
                `/datasets/${encodeURIComponent(params.id)}`,
                GeodataModel.upstream.jobResponse,
                { method: "DELETE" },
            );
            set.status = status;
            return simplifyJobResponse(result);
        },
        {
            admin: true,
            response: GeodataModel.jobResponse,
        },
    );
