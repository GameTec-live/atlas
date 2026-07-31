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

const jobBody = {
    assignedDriverId: "user-1",
    vehicleId: "7bb0de4d-bcdd-4c99-a852-a17a4bbdb3de",
    from: [48.2082, 16.3738],
    to: [48.1947, 16.3122],
    dueDate: "2026-08-01T12:00:00.000Z",
    note: "Deliver package to destination",
};

const request = (authenticated = true) => {
    const headers = new Headers();
    if (authenticated) headers.set("authorization", "Bearer test-token");

    return app.handle(
        new Request("http://localhost/jobs/assigned", { headers }),
    );
};

const createRequest = (
    body: unknown,
    authenticated = true,
    contentType = "application/json",
) => {
    const headers = new Headers({ "content-type": contentType });
    if (authenticated) headers.set("authorization", "Bearer test-token");

    return app.handle(
        new Request("http://localhost/jobs/create", {
            method: "POST",
            headers,
            body: JSON.stringify(body),
        }),
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

describe("POST /jobs/create", () => {
    it("returns 401 without a session and does not create a job", async () => {
        const response = await createRequest(jobBody, false);

        expect(response.status).toBe(401);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("creates a job using every supported field", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await createRequest(jobBody);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            message: "Job created successfully",
        });
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);

        const { sql, values } = getFirstQuery();
        expect(sql).toContain('insert into "job"');
        expect(sql).toContain(
            "values (default, $1, $2, $3, $4, $5, $6, default, default, default, default)",
        );
        expect(values).toEqual([
            jobBody.assignedDriverId,
            jobBody.vehicleId,
            "(48.2082,16.3738)",
            "(48.1947,16.3122)",
            jobBody.dueDate,
            jobBody.note,
        ]);
    });

    it("creates a job with only the required origin", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await createRequest({ from: [0, 0] });

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            message: "Job created successfully",
        });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);

        const { sql, values } = getFirstQuery();
        expect(sql).toContain(
            "values (default, default, default, $1, default, default, default, default, default, default, default)",
        );
        expect(values).toEqual(["(0,0)"]);
    });

    it("accepts inclusive coordinate boundaries", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await createRequest({
            from: [-90, -180],
            to: [90, 180],
        });

        expect(response.status).toBe(200);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
        expect(getFirstQuery().values).toEqual(["(-90,-180)", "(90,180)"]);
    });

    it("accepts nullable foreign keys and note", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await createRequest({
            from: [48.2082, 16.3738],
            assignedDriverId: null,
            vehicleId: null,
            note: null,
        });

        expect(response.status).toBe(200);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
        expect(getFirstQuery().values).toEqual([
            null,
            null,
            "(48.2082,16.3738)",
            null,
        ]);
    });

    it("discards surplus coordinates before inserting the job", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await createRequest({
            from: [48.2082, 16.3738, 123],
            to: [48.1947, 16.3122, 456],
        });

        expect(response.status).toBe(200);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
        expect(getFirstQuery().values).toEqual([
            "(48.2082,16.3738)",
            "(48.1947,16.3122)",
        ]);
    });

    it("discards server-managed fields before inserting the job", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await createRequest({
            ...jobBody,
            id: "b0f17458-94c5-47c9-ab4b-68aadf088f4c",
            startedAt: "2026-08-01T12:00:00.000Z",
            completedAt: "2026-08-01T13:00:00.000Z",
            createdAt: "2026-07-31T12:00:00.000Z",
            updatedAt: "2026-07-31T13:00:00.000Z",
        });

        expect(response.status).toBe(200);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);

        const { sql, values } = getFirstQuery();
        expect(sql).toContain(
            "values (default, $1, $2, $3, $4, $5, $6, default, default, default, default)",
        );
        expect(values).toEqual([
            jobBody.assignedDriverId,
            jobBody.vehicleId,
            "(48.2082,16.3738)",
            "(48.1947,16.3122)",
            jobBody.dueDate,
            jobBody.note,
        ]);
    });

    it.each([
        ["a missing origin", { ...jobBody, from: undefined }],
        ["a null origin", { ...jobBody, from: null }],
        ["an origin with too few coordinates", { ...jobBody, from: [0] }],
        [
            "a non-numeric origin coordinate",
            { ...jobBody, from: ["48.2082", 16.3738] },
        ],
        ["an origin latitude below -90", { ...jobBody, from: [-90.01, 0] }],
        ["an origin latitude above 90", { ...jobBody, from: [90.01, 0] }],
        ["an origin longitude below -180", { ...jobBody, from: [0, -180.01] }],
        ["an origin longitude above 180", { ...jobBody, from: [0, 180.01] }],
        [
            "a destination latitude outside its range",
            { ...jobBody, to: [91, 0] },
        ],
        [
            "a destination longitude outside its range",
            { ...jobBody, to: [0, 181] },
        ],
        ["a null destination", { ...jobBody, to: null }],
        ["a destination with too few coordinates", { ...jobBody, to: [0] }],
        [
            "a non-string assigned driver id",
            { ...jobBody, assignedDriverId: 123 },
        ],
        ["an invalid vehicle id", { ...jobBody, vehicleId: "not-a-uuid" }],
        ["an invalid due date", { ...jobBody, dueDate: "not-a-date" }],
        ["a non-string note", { ...jobBody, note: 123 }],
    ])("returns 422 for %s", async (_description, body) => {
        getSessionMock.mockResolvedValue(session);

        const response = await createRequest(body);

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("returns 500 when the job insert fails", async () => {
        getSessionMock.mockResolvedValue(session);
        dbClientQueryMock.mockRejectedValueOnce(
            new Error("database unavailable"),
        );

        const response = await createRequest(jobBody);

        expect(response.status).toBe(500);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });
});
