import { beforeEach, describe, expect, it } from "bun:test";
import { Elysia } from "elysia";
import { getSessionMock, resetAuthMocks, session } from "../mocks/auth";
import {
    dbClientQueryMock,
    exampleData,
    resetDbMocks,
    setDbMockRows,
} from "../mocks/db";

const { jobs } = await import("@/src/jobs");
const app = new Elysia().use(jobs);

const request = (authenticated = true) => {
    const headers = new Headers();
    if (authenticated) headers.set("authorization", "Bearer test-token");

    return app.handle(
        new Request("http://localhost/jobs/assigned", { headers }),
    );
};

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

const exampleJob = exampleData.job[0];
if (!exampleJob) throw new Error("Expected job fixture data");

const serializedJob = {
    ...exampleJob,
    dueDate: exampleJob.dueDate.toISOString(),
    startedAt: null,
    completedAt: null,
    createdAt: exampleJob.createdAt.toISOString(),
    updatedAt: exampleJob.updatedAt.toISOString(),
};

beforeEach(() => {
    resetAuthMocks();
    resetDbMocks();
});

describe("GET /jobs/assigned", () => {
    it("returns 401 without a session and does not query the database", async () => {
        const response = await request(false);

        expect(response.status).toBe(401);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("returns the jobs assigned to the authenticated user", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await request();

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([serializedJob]);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);

        const { sql, values } = getFirstQuery();
        expect(sql).toContain(
            'from "job" where "job"."assigned_driver_id" = $1',
        );
        expect(values).toEqual([session.user.id]);
    });

    it("uses the current session user when selecting assigned jobs", async () => {
        const otherSession = {
            ...session,
            user: {
                ...session.user,
                id: "another-driver",
            },
        };
        getSessionMock.mockResolvedValue(otherSession);
        setDbMockRows("select", []);

        const response = await request();

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([]);
        expect(getFirstQuery().values).toEqual([otherSession.user.id]);
    });

    it("returns an empty list when the user has no assigned jobs", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockRows("select", []);

        const response = await request();

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([]);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });

    it("returns 500 when the job lookup fails", async () => {
        getSessionMock.mockResolvedValue(session);
        dbClientQueryMock.mockRejectedValueOnce(
            new Error("database unavailable"),
        );

        const response = await request();

        expect(response.status).toBe(500);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });
});
