import { beforeEach, describe, expect, it } from "bun:test";
import { Elysia } from "elysia";
import { DatabaseError } from "pg";
import { getSessionMock, resetAuthMocks, session } from "../mocks/auth";
import { dbClientQueryMock, resetDbMocks, setDbMockRows } from "../mocks/db";
import "../mocks/env";

const { config } = await import("@/src/config");
const { roles } = await import("@/src/role");
const app = new Elysia().use(roles);

const driverId = session.user.id;
const secondDriverId = "user-2";
const driverName = session.user.name;

const request = (method = "GET", body?: unknown, authenticated = true) => {
    const headers = new Headers();
    if (authenticated) headers.set("authorization", "Bearer test-token");
    if (body !== undefined) headers.set("content-type", "application/json");

    return app.handle(
        new Request("http://localhost/roles/", {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body),
        }),
    );
};

const queryAt = (index: number) => {
    const call = dbClientQueryMock.mock.calls[index];
    if (!call) throw new Error(`Expected database call ${index + 1}`);

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

const queryResult = (rows: unknown[][] = []) => ({
    command: "SELECT",
    rowCount: rows.length,
    oid: 0,
    fields: [],
    rows,
});

const postgresError = (code: string) => {
    const error = new DatabaseError("test database error", 0, "error");
    error.code = code;
    return error;
};

beforeEach(() => {
    resetAuthMocks();
    resetDbMocks();
});

describe("roles authentication", () => {
    it.each([
        ["GET", undefined],
        ["POST", { role: "driver" }],
    ])("returns 401 for an unauthenticated %s", async (method, body) => {
        const response = await request(method, body, false);

        expect(response.status).toBe(401);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it.each([
        ["GET", undefined],
        ["POST", { role: "driver" }],
    ])("allows an authenticated non-admin to use %s", async (method, body) => {
        getSessionMock.mockResolvedValue(session);

        const response = await request(method, body);

        expect(response.status).toBe(200);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).toHaveBeenCalled();
    });
});

describe("GET /roles/", () => {
    it("returns today's roles and dispatcher capacity metadata", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockRows("select", [
            [driverId, "driver", driverName],
            [secondDriverId, "dispatcher", "Second Driver"],
            ["user-3", "dispatcher", "Third Driver"],
        ]);

        const response = await request();

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            roles: [
                { driverId, role: "driver", name: driverName },
                {
                    driverId: secondDriverId,
                    role: "dispatcher",
                    name: "Second Driver",
                },
                {
                    driverId: "user-3",
                    role: "dispatcher",
                    name: "Third Driver",
                },
            ],
            count: 3,
            dispatchers: 2,
            maxDispatchers: config.dispatchers.max,
            numFree: config.dispatchers.max - 2,
            free: config.dispatchers.max > 2,
        });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);

        const { sql, values } = queryAt(0);
        expect(sql).toContain(
            'select "role"."driver_id", "role"."role", "user"."name" from "role"',
        );
        expect(sql).toContain(
            'inner join "user" on "role"."driver_id" = "user"."id"',
        );
        expect(sql).toContain('"role"."date" = current_date');
        expect(values).toHaveLength(0);
    });

    it("returns an empty, fully available summary when nobody has a role", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockRows("select", []);

        const response = await request();

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            roles: [],
            count: 0,
            dispatchers: 0,
            maxDispatchers: config.dispatchers.max,
            numFree: config.dispatchers.max,
            free: config.dispatchers.max > 0,
        });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });

    it("returns 500 when the role lookup fails", async () => {
        getSessionMock.mockResolvedValue(session);
        dbClientQueryMock.mockRejectedValueOnce(
            new Error("database unavailable"),
        );

        const response = await request();

        expect(response.status).toBe(500);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });
});

describe("POST /roles/", () => {
    it("claims a driver role for the authenticated user without querying dispatcher capacity", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await request("POST", {
            role: "driver",
        });

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            message: "Role claimed successfully",
        });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);

        const { sql, values } = queryAt(0);
        expect(sql).toContain('insert into "role"');
        expect(sql).not.toContain("count(");
        expect(values).toEqual([driverId, "driver"]);
    });

    it("claims a dispatcher role when capacity is available", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockRows("count", [[Math.max(0, config.dispatchers.max - 1)]]);

        const response = await request("POST", {
            role: "dispatcher",
        });

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            message: "Role claimed successfully",
        });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(5);

        expect(queryAt(0).sql).toBe("begin");

        const lockQuery = queryAt(1);
        expect(lockQuery.sql).toContain("pg_advisory_xact_lock");

        const capacityQuery = queryAt(2);
        expect(capacityQuery.sql).toContain('select count(*) from "role"');
        expect(capacityQuery.sql).toContain('"role"."role" = $1');
        expect(capacityQuery.sql).toContain('"role"."date" = current_date');
        expect(capacityQuery.values).toHaveLength(1);
        expect(capacityQuery.values[0]).toBe("dispatcher");

        const insertQuery = queryAt(3);
        expect(insertQuery.sql).toContain('insert into "role"');
        expect(insertQuery.values).toEqual([driverId, "dispatcher"]);
        expect(queryAt(4).sql).toBe("commit");
    });

    it("returns 418 and does not insert when dispatcher capacity is full", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockRows("count", [[config.dispatchers.max]]);

        const response = await request("POST", {
            role: "dispatcher",
        });

        expect(response.status).toBe(418);
        expect(await response.json()).toEqual({
            error: "Max number of dispatchers reached",
        });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(4);
        expect(queryAt(0).sql).toBe("begin");
        expect(queryAt(1).sql).toContain("pg_advisory_xact_lock");
        expect(queryAt(2).sql).toContain("select count(*)");
        expect(queryAt(3).sql).toBe("commit");
        expect(
            dbClientQueryMock.mock.calls.some(([query]) => {
                if (
                    typeof query !== "object" ||
                    query === null ||
                    !("text" in query)
                ) {
                    return false;
                }
                return String(query.text).startsWith("insert");
            }),
        ).toBe(false);
    });

    it("allows only one of two concurrent claims for the final dispatcher slot", async () => {
        getSessionMock.mockResolvedValue(session);

        let dispatcherCount = Math.max(0, config.dispatchers.max - 1);
        let locked = false;
        const lockWaiters: Array<() => void> = [];

        const acquireLock = () =>
            new Promise<void>((resolve) => {
                if (!locked) {
                    locked = true;
                    resolve();
                    return;
                }
                lockWaiters.push(() => {
                    locked = true;
                    resolve();
                });
            });

        const releaseLock = () => {
            locked = false;
            lockWaiters.shift()?.();
        };

        dbClientQueryMock.mockImplementation(async (query) => {
            const sql =
                typeof query === "object" &&
                query !== null &&
                "text" in query &&
                typeof query.text === "string"
                    ? query.text.toLowerCase()
                    : "";

            if (sql.includes("pg_advisory_xact_lock")) {
                await acquireLock();
            } else if (sql.startsWith("select count(")) {
                return queryResult([[dispatcherCount]]);
            } else if (sql.startsWith('insert into "role"')) {
                dispatcherCount++;
            } else if (sql === "commit" || sql === "rollback") {
                releaseLock();
            }

            return queryResult();
        });

        const responses = await Promise.all([
            request("POST", {
                role: "dispatcher",
            }),
            request("POST", {
                role: "dispatcher",
            }),
        ]);

        expect(responses.map(({ status }) => status).sort()).toEqual([
            200, 418,
        ]);
        expect(dispatcherCount).toBe(config.dispatchers.max);
    });

    it("accepts an explicit assignment date", async () => {
        getSessionMock.mockResolvedValue(session);
        const date = "2026-07-24T00:00:00.000Z";

        const response = await request("POST", {
            role: "driver",
            date,
        });

        expect(response.status).toBe(200);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);

        const { values } = queryAt(0);
        expect(values).toEqual([driverId, "driver", date]);
    });

    it("does not allow the request body to assign a role to another driver", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await request("POST", {
            driverId: secondDriverId,
            role: "driver",
        });

        expect(response.status).toBe(200);
        expect(queryAt(0).values).toEqual([driverId, "driver"]);
    });

    it.each([
        ["a missing role", {}],
        ["an unsupported role", { role: "admin" }],
        ["an invalid date", { role: "driver", date: "not-a-date" }],
    ])("returns 422 for %s without querying the database", async (_label, body) => {
        getSessionMock.mockResolvedValue(session);

        const response = await request("POST", body);

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it.each([
        ["23505", 409, { error: "Role already claimed" }],
        ["23503", 400, { error: "Driver does not exist" }],
    ])("maps PostgreSQL error %s to status %i", async (code, expectedStatus, expectedBody) => {
        getSessionMock.mockResolvedValue(session);
        dbClientQueryMock.mockRejectedValueOnce(postgresError(code));

        const response = await request("POST", {
            role: "driver",
        });

        expect(response.status).toBe(expectedStatus);
        expect(await response.json()).toEqual(expectedBody);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });

    it("returns 500 for an unrecognized PostgreSQL error", async () => {
        getSessionMock.mockResolvedValue(session);
        dbClientQueryMock.mockRejectedValueOnce(postgresError("08006"));

        const response = await request("POST", {
            role: "driver",
        });

        expect(response.status).toBe(500);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });

    it("returns 500 when the dispatcher capacity query fails", async () => {
        getSessionMock.mockResolvedValue(session);
        dbClientQueryMock
            .mockResolvedValueOnce(queryResult())
            .mockResolvedValueOnce(queryResult())
            .mockRejectedValueOnce(new Error("database unavailable"));

        const response = await request("POST", {
            role: "dispatcher",
        });

        expect(response.status).toBe(500);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(4);
        expect(queryAt(3).sql).toBe("rollback");
    });
});
