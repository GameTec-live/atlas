import { beforeEach, describe, expect, it } from "bun:test";
import { Elysia } from "elysia";
import { getSessionMock, resetAuthMocks, session } from "../mocks/auth";
import {
    dbClientQueryMock,
    exampleData,
    resetDbMocks,
    setDbMockRows,
} from "../mocks/db";

const { shortnames } = await import("@/src/shortnames");
const app = new Elysia().use(shortnames);

const adminSession = {
    ...session,
    user: {
        ...session.user,
        role: "admin",
    },
};

const exampleShortname = exampleData.shortname[0];
if (!exampleShortname) throw new Error("Expected shortname fixture data");

const request = (path: string, init: RequestInit = {}) => {
    const headers = new Headers(init.headers);
    headers.set("authorization", "Bearer test-token");

    return app.handle(
        new Request(`http://localhost/shortnames${path}`, {
            ...init,
            headers,
        }),
    );
};

const jsonRequest = (path: string, method: string, body: unknown) =>
    request(path, {
        method,
        headers: { "content-type": "application/json" },
        body: JSON.stringify(body),
    });

const getFirstQuery = () => {
    const call = dbClientQueryMock.mock.calls[0];
    if (!call) throw new Error("Expected a database call");

    const query = call[0];
    if (
        typeof query !== "object" ||
        query === null ||
        !("text" in query) ||
        typeof query.text !== "string"
    ) {
        throw new Error("Expected the database call to contain SQL text");
    }

    return { sql: query.text, values: call[1] as unknown[] };
};

beforeEach(() => {
    resetAuthMocks();
    resetDbMocks();
});

describe("shortname authentication", () => {
    it.each([
        ["GET", "/", undefined],
        ["GET", "/primary-depot", undefined],
        ["POST", "/", { key: "airport", value: "Vienna Airport" }],
        ["PUT", "/primary-depot", { value: "New depot address" }],
        ["DELETE", "/primary-depot", undefined],
    ])("returns 401 for an unauthenticated %s %s", async (method, path, body) => {
        const response = await app.handle(
            new Request(`http://localhost/shortnames${path}`, {
                method,
                headers:
                    body === undefined
                        ? undefined
                        : { "content-type": "application/json" },
                body: body === undefined ? undefined : JSON.stringify(body),
            }),
        );

        expect(response.status).toBe(401);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it.each([
        ["GET", "/", undefined],
        ["GET", "/primary-depot", undefined],
        ["POST", "/", { key: "airport", value: "Vienna Airport" }],
        ["PUT", "/primary-depot", { value: "New depot address" }],
        ["DELETE", "/primary-depot", undefined],
    ])("returns 403 for a non-admin %s %s", async (method, path, body) => {
        getSessionMock.mockResolvedValue(session);

        const response = await request(path, {
            method,
            headers:
                body === undefined
                    ? undefined
                    : { "content-type": "application/json" },
            body: body === undefined ? undefined : JSON.stringify(body),
        });

        expect(response.status).toBe(403);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });
});

describe("GET /shortnames", () => {
    it("lists shortnames ordered by key", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await request("/");

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual(exampleData.shortname);
        const { sql, values } = getFirstQuery();
        expect(sql).toContain('from "shortname" order by "shortname"."key"');
        expect(values).toEqual([]);
    });
});

describe("GET /shortnames/:key", () => {
    it("returns a shortname and normalizes the lookup key", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await request("/PRIMARY-DEPOT");

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual(exampleShortname);
        expect(getFirstQuery().values).toEqual(["primary-depot", 1]);
    });

    it("returns 404 when the shortname does not exist", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        setDbMockRows("select", []);

        const response = await request("/unknown");

        expect(response.status).toBe(404);
        expect(await response.json()).toEqual({ error: "Shortname not found" });
    });
});

describe("POST /shortnames", () => {
    it("creates a shortname with a normalized key", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        setDbMockRows("insert", [["airport", "Vienna Airport"]]);

        const response = await jsonRequest("/", "POST", {
            key: "AIRPORT",
            value: "Vienna Airport",
        });

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            key: "airport",
            value: "Vienna Airport",
        });
        const { sql, values } = getFirstQuery();
        expect(sql).toContain('insert into "shortname"');
        expect(sql).toContain("on conflict");
        expect(values).toEqual(["airport", "Vienna Airport"]);
    });

    it("returns 409 when the key already exists", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await jsonRequest("/", "POST", exampleShortname);

        expect(response.status).toBe(409);
        expect(await response.json()).toEqual({
            error: "Shortname already exists",
        });
    });

    it("returns 422 for an empty key", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await jsonRequest("/", "POST", {
            key: "",
            value: "Vienna Airport",
        });

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });
});

describe("PUT /shortnames/:key", () => {
    it("updates and returns an existing shortname", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        setDbMockRows("update", [["primary-depot", "New depot address"]]);

        const response = await jsonRequest("/PRIMARY-DEPOT", "PUT", {
            value: "New depot address",
        });

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            key: "primary-depot",
            value: "New depot address",
        });
        expect(getFirstQuery().values).toEqual([
            "New depot address",
            "primary-depot",
        ]);
    });

    it("returns 404 when the shortname does not exist", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await jsonRequest("/unknown", "PUT", {
            value: "New address",
        });

        expect(response.status).toBe(404);
        expect(await response.json()).toEqual({ error: "Shortname not found" });
    });
});

describe("DELETE /shortnames/:key", () => {
    it("deletes and returns an existing shortname", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        setDbMockRows("delete", [
            [exampleShortname.key, exampleShortname.value],
        ]);

        const response = await request("/PRIMARY-DEPOT", {
            method: "DELETE",
        });

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual(exampleShortname);
        expect(getFirstQuery().values).toEqual(["primary-depot"]);
    });

    it("returns 404 when the shortname does not exist", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await request("/unknown", { method: "DELETE" });

        expect(response.status).toBe(404);
        expect(await response.json()).toEqual({ error: "Shortname not found" });
    });
});
