import { afterAll, beforeEach, describe, expect, it, mock } from "bun:test";
import { Elysia } from "elysia";
import { getSessionMock, resetAuthMocks, session } from "../mocks/auth";
import {
    dbClientQueryMock,
    resetDbMocks,
    setDbMockTableRows,
} from "../mocks/db";

const GEOCODER_URL = "http://geocoder.test";
const ROUTER_URL = "http://router.test";

mock.module("@/env", () => ({
    env: {
        BETTER_AUTH_SECRET: "test-secret-that-is-at-least-32-characters",
        BETTER_AUTH_URL: "http://auth.test",
        DATABASE_URL: "postgresql://test:test@database.test/test",
        GEOCODER_URL,
        ROUTER_URL,
    },
}));

const { config } = await import("@/src/config");
const { geoservices } = await import("@/src/geoservices");
const app = new Elysia().use(geoservices);

const originalFetch = globalThis.fetch;
const fetchMock = mock(
    async (
        _input: string | URL | Request,
        _init?: RequestInit,
    ): Promise<Response> => {
        throw new Error("Unexpected geocoder request");
    },
);

const successResponse = {
    query: "Vienna Central Depot",
    count: 1,
    results: [
        {
            pack: "openstreetmap",
            source: "osm",
            source_id: "node/123",
            kind: "place",
            name: "Vienna Central Depot",
            house_number: "1",
            street: "Depot Street",
            unit: "A",
            postcode: "1010",
            locality: "Vienna",
            district: "Innere Stadt",
            region: "Vienna",
            country_code: "at",
            country: "Austria",
            lat: 48.2082,
            lon: 16.3738,
            importance: 0.9,
            aliases: ["Central Depot"],
            display_name: "Vienna Central Depot, Austria",
            score: 0.98,
            distance_m: 12.5,
        },
    ],
};

const errorResponse = {
    error: {
        code: "NO_RESULTS",
        message: "No matching location was found",
    },
};

const routeSummary = {
    has_time_restrictions: false,
    has_toll: false,
    has_highway: true,
    has_ferry: false,
    min_lat: 48.2082,
    min_lon: 16.3738,
    max_lat: 48.3069,
    max_lon: 16.437,
    time: 1_200,
    length: 12.5,
    cost: 1_350.5,
};

const routeSuccessResponse = {
    trip: {
        locations: [
            {
                type: "break",
                lat: 48.2082,
                lon: 16.3738,
                original_index: 0,
            },
            {
                type: "break",
                lat: 48.3069,
                lon: 16.437,
                original_index: 1,
            },
        ],
        legs: [
            {
                summary: routeSummary,
                shape: "encoded-route-shape",
            },
        ],
        summary: routeSummary,
        status_message: "Found route between points",
        status: 0,
        units: "kilometers",
        language: "en-US",
    },
};

const routeErrorResponse = {
    error_code: 171,
    error: "No suitable edges near location",
    status_code: 400,
    status: "Bad Request",
};

const request = (address?: string) => {
    const url = new URL("http://localhost/geoservices/resolve");
    if (address !== undefined) url.searchParams.set("address", address);

    return app.handle(
        new Request(url.toString(), {
            headers: { authorization: "Bearer test-token" },
        }),
    );
};

const respondWith = (body: unknown, init?: ResponseInit) => {
    fetchMock.mockResolvedValueOnce(Response.json(body, init));
};

const validRouteQuery = {
    fromlat: 48.2082,
    fromlon: 16.3738,
    tolat: 48.3069,
    tolon: 16.437,
};

const routeRequest = (
    query: Partial<
        Record<keyof typeof validRouteQuery | "lang", string | number>
    >,
) => {
    const url = new URL("http://localhost/geoservices/route");
    for (const [key, value] of Object.entries(query)) {
        url.searchParams.set(key, String(value));
    }

    return app.handle(
        new Request(url.toString(), {
            headers: { authorization: "Bearer test-token" },
        }),
    );
};

describe("GET /geoservices/resolve", () => {
    beforeEach(() => {
        resetAuthMocks();
        resetDbMocks();
        fetchMock.mockReset();
        globalThis.fetch = fetchMock as unknown as typeof fetch;
    });

    afterAll(() => {
        globalThis.fetch = originalFetch;
    });

    it("returns 401 without a Better Auth session", async () => {
        const response = await request("unauthenticated-address");

        expect(response.status).toBe(401);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it.each([
        ["a missing address", undefined],
        ["an empty address", ""],
    ])("returns 422 for %s", async (_description, address) => {
        getSessionMock.mockResolvedValue(session);

        const response = await request(address);

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it("geocodes the original address when no shortname matches", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockTableRows("shortname", []);
        respondWith(successResponse);
        const address = "Landstraßer Hauptstraße 1, Wien & Umgebung";

        const response = await request(address);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual(successResponse);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock.mock.calls[0]?.[0]).toMatchObject({
            text: expect.stringContaining('from "shortname"'),
        });
        expect(dbClientQueryMock.mock.calls[0]?.[1]).toEqual([
            address.toLowerCase(),
            1,
        ]);
        expect(fetchMock).toHaveBeenCalledTimes(1);
        expect(fetchMock).toHaveBeenCalledWith(
            `${GEOCODER_URL}/geocode?q=${encodeURIComponent(address)}`,
            { method: "GET" },
        );
    });

    it("resolves shortnames case-insensitively before geocoding", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockTableRows("shortname", [
            ["primary-depot", "Vienna Central Depot / Gate 2"],
        ]);
        respondWith(successResponse);

        const response = await request("PRIMARY-DEPOT");

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual(successResponse);
        expect(dbClientQueryMock.mock.calls[0]?.[1]).toEqual([
            "primary-depot",
            1,
        ]);
        expect(fetchMock).toHaveBeenCalledWith(
            `${GEOCODER_URL}/geocode?q=Vienna%20Central%20Depot%20%2F%20Gate%202`,
            { method: "GET" },
        );
    });

    it("returns a schema-valid error response from the geocoder", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockTableRows("shortname", []);
        respondWith(errorResponse);

        const response = await request("valid-upstream-error-address");

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual(errorResponse);
    });

    it("serves repeated exact addresses from cache while revalidating the shortname", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockTableRows("shortname", []);
        respondWith(successResponse);

        const firstResponse = await request("cache-hit-address");
        const secondResponse = await request("cache-hit-address");

        expect(firstResponse.status).toBe(200);
        expect(secondResponse.status).toBe(200);
        expect(await secondResponse.json()).toEqual(successResponse);
        expect(getSessionMock).toHaveBeenCalledTimes(2);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(2);
        expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it("evicts a cached response when its shortname changes", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockTableRows("shortname", [
            ["changing-shortname", "Old address"],
        ]);
        respondWith({ ...successResponse, query: "Old address" });

        const firstResponse = await request("changing-shortname");

        setDbMockTableRows("shortname", [
            ["changing-shortname", "New address"],
        ]);
        const cachedResponse = await request("changing-shortname");

        expect(await firstResponse.json()).toMatchObject({
            query: "Old address",
        });
        expect(await cachedResponse.json()).toMatchObject({
            query: "Old address",
        });
        expect(fetchMock).toHaveBeenCalledTimes(1);

        await new Promise((resolve) => setTimeout(resolve, 0));
        respondWith({ ...successResponse, query: "New address" });

        const refreshedResponse = await request("changing-shortname");

        expect(await refreshedResponse.json()).toMatchObject({
            query: "New address",
        });
        expect(fetchMock).toHaveBeenCalledTimes(2);
        expect(fetchMock).toHaveBeenLastCalledWith(
            `${GEOCODER_URL}/geocode?q=New%20address`,
            { method: "GET" },
        );
    });

    it("keeps serving a cached response when background revalidation fails", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockTableRows("shortname", []);
        respondWith(successResponse);

        expect((await request("revalidation-error-address")).status).toBe(200);
        dbClientQueryMock.mockRejectedValueOnce(
            new Error("database temporarily unavailable"),
        );

        expect((await request("revalidation-error-address")).status).toBe(200);
        await new Promise((resolve) => setTimeout(resolve, 0));
        expect((await request("revalidation-error-address")).status).toBe(200);
        expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it("uses the exact address as the cache key", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockTableRows("shortname", []);
        respondWith({ ...successResponse, query: "case-sensitive-address" });
        respondWith({ ...successResponse, query: "CASE-SENSITIVE-ADDRESS" });

        const lowerCaseResponse = await request("case-sensitive-address");
        const upperCaseResponse = await request("CASE-SENSITIVE-ADDRESS");

        expect(await lowerCaseResponse.json()).toMatchObject({
            query: "case-sensitive-address",
        });
        expect(await upperCaseResponse.json()).toMatchObject({
            query: "CASE-SENSITIVE-ADDRESS",
        });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(2);
        expect(fetchMock).toHaveBeenCalledTimes(2);
    });

    it("returns 500 and does not cache a response that violates the geocoder schema", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockTableRows("shortname", []);
        const invalidResponse = {
            query: "invalid-schema-address",
            count: 1,
            results: [{ display_name: "Missing required coordinates" }],
        };
        respondWith(invalidResponse);
        respondWith(invalidResponse);

        const firstResponse = await request("invalid-schema-address");
        const secondResponse = await request("invalid-schema-address");

        expect(firstResponse.status).toBe(500);
        expect(secondResponse.status).toBe(500);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(2);
        expect(fetchMock).toHaveBeenCalledTimes(2);
    });

    it("returns 500 when the geocoder request fails", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockTableRows("shortname", []);
        fetchMock.mockRejectedValueOnce(new Error("geocoder unavailable"));

        const response = await request("unavailable-geocoder-address");

        expect(response.status).toBe(500);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
        expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it("returns 500 without geocoding when the shortname lookup fails", async () => {
        getSessionMock.mockResolvedValue(session);
        dbClientQueryMock.mockRejectedValueOnce(
            new Error("database unavailable"),
        );

        const response = await request("unavailable-database-address");

        expect(response.status).toBe(500);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it("refreshes cache recency and evicts the least recently used entry", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockTableRows("shortname", []);
        fetchMock.mockImplementation(async (input) => {
            const query = new URL(String(input)).searchParams.get("q") ?? "";
            return Response.json({ ...successResponse, query });
        });
        const refreshedAddress = "lru-refreshed-address";

        expect((await request(refreshedAddress)).status).toBe(200);
        for (let index = 0; index < 999; index += 1) {
            expect((await request(`lru-filler-${index}`)).status).toBe(200);
        }

        const callsBeforeCacheHit = fetchMock.mock.calls.length;
        expect((await request(refreshedAddress)).status).toBe(200);
        expect(fetchMock).toHaveBeenCalledTimes(callsBeforeCacheHit);

        expect((await request("lru-overflow-address")).status).toBe(200);
        expect((await request(refreshedAddress)).status).toBe(200);
        expect(fetchMock).toHaveBeenCalledTimes(callsBeforeCacheHit + 1);

        expect((await request("lru-filler-0")).status).toBe(200);
        expect(fetchMock).toHaveBeenCalledTimes(callsBeforeCacheHit + 2);
    });
});

describe("GET /geoservices/route", () => {
    beforeEach(() => {
        resetAuthMocks();
        resetDbMocks();
        fetchMock.mockReset();
        globalThis.fetch = fetchMock as unknown as typeof fetch;
    });

    afterAll(() => {
        globalThis.fetch = originalFetch;
    });

    it("returns 401 without a Better Auth session", async () => {
        const response = await routeRequest(validRouteQuery);

        expect(response.status).toBe(401);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it.each([
        ["a missing coordinate", { ...validRouteQuery, tolon: undefined }],
        ["a non-numeric coordinate", { ...validRouteQuery, fromlat: "north" }],
        ["an out-of-range latitude", { ...validRouteQuery, tolat: 91 }],
        ["an invalid language", { ...validRouteQuery, lang: "english" }],
    ])("returns 422 for %s", async (_description, query) => {
        getSessionMock.mockResolvedValue(session);
        const sanitizedQuery = Object.fromEntries(
            Object.entries(query).filter(([, value]) => value !== undefined),
        );

        const response = await routeRequest(sanitizedQuery);

        expect(response.status).toBe(422);
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it("requests an auto route with the default language", async () => {
        getSessionMock.mockResolvedValue(session);
        respondWith(routeSuccessResponse);

        const response = await routeRequest(validRouteQuery);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual(routeSuccessResponse);
        expect(fetchMock).toHaveBeenCalledTimes(1);
        expect(fetchMock.mock.calls[0]?.[1]).toEqual({ method: "GET" });

        const routerRequestUrl = new URL(String(fetchMock.mock.calls[0]?.[0]));
        expect(`${routerRequestUrl.origin}${routerRequestUrl.pathname}`).toBe(
            `${ROUTER_URL}/route`,
        );
        expect(
            JSON.parse(routerRequestUrl.searchParams.get("json") ?? ""),
        ).toEqual({
            locations: [
                {
                    options: { allowUTurn: false },
                    latLng: { lat: 48.2082, lng: 16.3738 },
                    _initHooksCalled: true,
                    lat: 48.2082,
                    lon: 16.3738,
                },
                {
                    options: { allowUTurn: false },
                    latLng: { lat: 48.3069, lng: 16.437 },
                    _initHooksCalled: true,
                    lat: 48.3069,
                    lon: 16.437,
                },
            ],
            costing: "auto",
            directions_options: {
                language: config.routing.defaultLanguage,
            },
        });
    });

    it("forwards the requested directions language", async () => {
        getSessionMock.mockResolvedValue(session);
        respondWith({
            ...routeSuccessResponse,
            trip: { ...routeSuccessResponse.trip, language: "de-AT" },
        });

        const response = await routeRequest({
            ...validRouteQuery,
            lang: "de-AT",
        });

        expect(response.status).toBe(200);
        const routerRequestUrl = new URL(String(fetchMock.mock.calls[0]?.[0]));
        const routerQuery = JSON.parse(
            routerRequestUrl.searchParams.get("json") ?? "",
        );
        expect(routerQuery.directions_options).toEqual({ language: "de-AT" });
    });

    it("preserves the status of a schema-valid error response from the router", async () => {
        getSessionMock.mockResolvedValue(session);
        respondWith(routeErrorResponse, { status: 400 });

        const response = await routeRequest(validRouteQuery);

        expect(response.status).toBe(400);
        expect(await response.json()).toEqual(routeErrorResponse);
    });

    it("returns 500 when the router response violates the schema", async () => {
        getSessionMock.mockResolvedValue(session);
        respondWith({ trip: { status: 0 } });

        const response = await routeRequest(validRouteQuery);

        expect(response.status).toBe(500);
        expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it("returns 500 when the router request fails", async () => {
        getSessionMock.mockResolvedValue(session);
        fetchMock.mockRejectedValueOnce(new Error("router unavailable"));

        const response = await routeRequest(validRouteQuery);

        expect(response.status).toBe(500);
        expect(fetchMock).toHaveBeenCalledTimes(1);
    });
});
