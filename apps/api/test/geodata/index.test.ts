import {
    afterAll,
    beforeAll,
    beforeEach,
    describe,
    expect,
    it,
    mock,
} from "bun:test";
import { Elysia } from "elysia";
import { getSessionMock, resetAuthMocks, session } from "../mocks/auth";

const upstream = Bun.serve({
    hostname: "127.0.0.1",
    port: 0,
    fetch(request, server) {
        if (
            new URL(request.url).pathname === "/api/v1/jobs/ws" &&
            server.upgrade(request)
        ) {
            return;
        }
        return new Response("Not found", { status: 404 });
    },
    websocket: {
        open(ws) {
            ws.send(JSON.stringify({ type: "snapshot", jobs: [job] }));
        },
        message() {},
    },
});
const GEODATA_URL = `http://127.0.0.1:${upstream.port}`;

mock.module("@/env", () => ({
    env: {
        GEODATA_URL,
    },
}));

const { geodata, GEODATA_TIMEOUT_MS } = await import("@/src/geodata");
const app = new Elysia().use(geodata);
let websocketUrl: string;

const originalFetch = globalThis.fetch;
const fetchMock = mock(
    async (
        _input: string | URL | Request,
        _init?: RequestInit,
    ): Promise<Response> => {
        throw new Error("Unexpected geodata request");
    },
);

const adminSession = {
    ...session,
    user: {
        ...session.user,
        role: "admin",
    },
};

const job = {
    id: "job-1",
    operation: "install",
    dataset_id: "austria",
    state: "queued",
    stage: "queued",
    progress: 0,
    bytes_done: 1024,
    bytes_total: 2048,
    created_at: "2026-08-11T10:00:00Z",
    request: {
        name: "austria",
    },
};

const publicJob = {
    id: job.id,
    operation: job.operation,
    dataset_id: job.dataset_id,
    state: job.state,
    stage: job.stage,
    progress: job.progress,
    created_at: job.created_at,
};

const request = (path: string, method = "GET", body?: unknown) => {
    const headers = new Headers({ authorization: "Bearer admin-token" });
    if (body !== undefined) headers.set("content-type", "application/json");

    return app.handle(
        new Request(`http://localhost/geodata${path}`, {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body),
        }),
    );
};

beforeAll(() => {
    app.listen({ hostname: "127.0.0.1", port: 0 });
    if (!app.server?.port) throw new Error("Geodata test server did not start");
    websocketUrl = `ws://127.0.0.1:${app.server.port}/geodata/jobs/live/ws`;
});

beforeEach(() => {
    resetAuthMocks();
    fetchMock.mockReset();
    fetchMock.mockImplementation(
        async (_input: string | URL | Request, _init?: RequestInit) => {
            throw new Error("Unexpected geodata request");
        },
    );
    globalThis.fetch = fetchMock as unknown as typeof fetch;
});

afterAll(async () => {
    globalThis.fetch = originalFetch;
    await app.stop(true);
    await upstream.stop(true);
});

describe("geodata API", () => {
    it("requires an admin session", async () => {
        expect((await request("/datasets")).status).toBe(401);

        getSessionMock.mockResolvedValue(session);
        expect((await request("/datasets")).status).toBe(403);
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it("forwards catalog filters and validates the response", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        const upstreamResult = {
            items: [
                {
                    id: "europe/austria",
                    name: "Austria",
                    parent: "europe",
                    country_codes: ["AT"],
                    pbf_url:
                        "https://download.geofabrik.de/europe/austria-latest.osm.pbf",
                },
            ],
            count: 1,
        };
        fetchMock.mockResolvedValueOnce(Response.json(upstreamResult));

        const response = await request(
            "/catalog?q=lower%20austria&parent=europe",
        );

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            items: [
                {
                    id: "europe/austria",
                    name: "Austria",
                    parent: "europe",
                },
            ],
            count: 1,
        });
        expect(fetchMock.mock.calls[0]?.[0]).toBe(
            `${GEODATA_URL}/api/v1/catalog?q=lower+austria&parent=europe`,
        );
    });

    it("preserves a schema-valid catalog error", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        const error = {
            error: {
                code: "catalog_unavailable",
                message: "Geofabrik is unavailable",
            },
        };
        fetchMock.mockResolvedValueOnce(Response.json(error, { status: 502 }));

        const response = await request("/catalog");

        expect(response.status).toBe(502);
        expect(await response.json()).toEqual(error);
    });

    it("forwards named dataset installs and preserves upstream errors", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        const error = {
            error: {
                code: "cannot_install_dataset",
                message: "dataset already installed",
            },
        };
        fetchMock.mockResolvedValueOnce(Response.json(error, { status: 409 }));

        const response = await request("/datasets", "POST", {
            name: "austria",
        });

        expect(response.status).toBe(409);
        expect(await response.json()).toEqual(error);
        expect(fetchMock.mock.calls[0]?.[0]).toBe(
            `${GEODATA_URL}/api/v1/datasets`,
        );
        expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
            method: "POST",
            body: JSON.stringify({
                name: "austria",
            }),
        });
    });

    it("turns a flat bounding box into the internal request", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        fetchMock.mockResolvedValueOnce(Response.json(job, { status: 202 }));

        const response = await request("/datasets", "POST", {
            id: "vienna",
            minLongitude: 16.17,
            minLatitude: 48.1,
            maxLongitude: 16.58,
            maxLatitude: 48.33,
        });

        expect(response.status).toBe(202);
        expect(await response.json()).toEqual(publicJob);
        expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
            method: "POST",
            body: JSON.stringify({
                id: "vienna",
                bbox: {
                    minLongitude: 16.17,
                    minLatitude: 48.1,
                    maxLongitude: 16.58,
                    maxLatitude: 48.33,
                },
            }),
        });
    });

    it("returns dataset metadata without internal URLs or paths", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        fetchMock.mockResolvedValueOnce(
            Response.json({
                items: [
                    {
                        id: "austria",
                        name: "Austria",
                        state: "ready",
                        source_type: "name",
                        source_region: "europe/austria",
                        source_url:
                            "https://download.geofabrik.de/europe/austria-latest.osm.pbf",
                        country_code: "AT",
                        artifacts: [
                            {
                                kind: "pbf",
                                path: "austria.osm.pbf",
                                size_bytes: 100,
                                modified_at: "2026-08-11T10:00:00Z",
                            },
                            {
                                kind: "geocoder",
                                path: "austria.sqlite",
                                size_bytes: 200,
                                modified_at: "2026-08-11T10:01:00Z",
                            },
                            {
                                kind: "map",
                                path: "map.pmtiles",
                                size_bytes: 300,
                                modified_at: "2026-08-11T10:02:00Z",
                            },
                        ],
                        installed_at: "2026-08-11T10:03:00Z",
                    },
                ],
                count: 1,
            }),
        );

        const response = await request("/datasets");

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            items: [
                {
                    id: "austria",
                    name: "Austria",
                    state: "ready",
                    source_type: "name",
                    size_bytes: { pbf: 100, geocoder: 200, map: 300 },
                    installed_at: "2026-08-11T10:03:00Z",
                },
            ],
            count: 1,
        });
    });

    it("forwards active job filters and dataset deletion", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        fetchMock
            .mockResolvedValueOnce(Response.json({ items: [job], count: 1 }))
            .mockResolvedValueOnce(Response.json(job, { status: 202 }));

        expect((await request("/jobs?active=true")).status).toBe(200);
        expect(fetchMock.mock.calls[0]?.[0]).toBe(
            `${GEODATA_URL}/api/v1/jobs?active=true`,
        );

        expect((await request("/datasets/austria", "DELETE")).status).toBe(202);
        expect(fetchMock.mock.calls[1]?.[0]).toBe(
            `${GEODATA_URL}/api/v1/datasets/austria`,
        );
        expect(fetchMock.mock.calls[1]?.[1]).toMatchObject({
            method: "DELETE",
        });
    });

    it("relays live job updates over an authenticated WebSocket", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const update = await new Promise<unknown>((resolve, reject) => {
            const socket = new WebSocket(websocketUrl, {
                headers: { authorization: "Bearer admin-token" },
            });
            const timeout = setTimeout(() => {
                socket.close();
                reject(new Error("Timed out waiting for geodata update"));
            }, 1_000);

            socket.addEventListener(
                "message",
                ({ data }) => {
                    clearTimeout(timeout);
                    resolve(JSON.parse(String(data)) as unknown);
                    socket.close();
                },
                { once: true },
            );
            socket.addEventListener("error", () => {}, { once: true });
            socket.addEventListener(
                "close",
                ({ code, reason }) => {
                    clearTimeout(timeout);
                    reject(
                        new Error(
                            `Geodata WebSocket closed before an update (${code}: ${reason})`,
                        ),
                    );
                },
                { once: true },
            );
        });

        expect(update).toEqual({ type: "snapshot", jobs: [publicJob] });
    });

    it("uses the same bounded request timeout as the geocoder proxy", () => {
        expect(GEODATA_TIMEOUT_MS).toBe(30_000);
    });
});
