import { beforeEach, describe, expect, it, mock } from "bun:test";
import { Elysia } from "elysia";
import { getSessionMock, resetAuthMocks, session } from "../mocks/auth";
import {
    dbClientQueryMock,
    exampleData,
    getDbMockTableRows,
    resetDbMocks,
    setDbMockRowCount,
    setDbMockRows,
} from "../mocks/db";

const envMock: { JOBTOKEN?: string } = {};

mock.module("@/env", () => ({
    env: envMock,
}));

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

const unassignedRequest = (authenticated = true) => {
    const headers = new Headers();
    if (authenticated) headers.set("authorization", "Bearer test-token");

    return app.handle(
        new Request("http://localhost/jobs/unassigned", { headers }),
    );
};

const unassignedReducedRequest = (token?: string) => {
    const headers = new Headers();
    if (token !== undefined) {
        headers.set("authorization", token);
    }

    return app.handle(
        new Request("http://localhost/jobs/unassigned-reduced", { headers }),
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

const jobId = "b0f17458-94c5-47c9-ab4b-68aadf088f4c";

const assignRequest = (
    body?: unknown,
    authenticated = true,
    id = jobId,
    contentType = "application/json",
) => {
    const headers = new Headers();
    if (authenticated) headers.set("authorization", "Bearer test-token");

    const init: RequestInit = { method: "POST", headers };
    if (body !== undefined) {
        headers.set("content-type", contentType);
        init.body = JSON.stringify(body);
    }

    return app.handle(new Request(`http://localhost/jobs/${id}/assign`, init));
};

const mutationRequest = (
    action: "start" | "complete" | "cancel",
    body?: unknown,
    authenticated = true,
    id = jobId,
    contentType = "application/json",
) => {
    const headers = new Headers();
    if (authenticated) headers.set("authorization", "Bearer test-token");

    const init: RequestInit = { method: "POST", headers };
    if (body !== undefined) {
        headers.set("content-type", contentType);
        init.body = JSON.stringify(body);
    }

    return app.handle(
        new Request(`http://localhost/jobs/${id}/${action}`, init),
    );
};

const updateRequest = (
    body: unknown,
    authenticated = true,
    id = jobId,
    contentType = "application/json",
) => {
    const headers = new Headers({ "content-type": contentType });
    if (authenticated) headers.set("authorization", "Bearer test-token");

    return app.handle(
        new Request(`http://localhost/jobs/${id}`, {
            method: "PUT",
            headers,
            body: JSON.stringify(body),
        }),
    );
};

const getJobRequest = (authenticated = true, id = jobId) => {
    const headers = new Headers();
    if (authenticated) headers.set("authorization", "Bearer test-token");

    return app.handle(new Request(`http://localhost/jobs/${id}`, { headers }));
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
    envMock.JOBTOKEN = undefined;
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

describe("GET /jobs/unassigned", () => {
    it("returns 401 without a session and does not query the database", async () => {
        const response = await unassignedRequest(false);

        expect(response.status).toBe(401);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("returns complete unassigned jobs ordered by due date", async () => {
        getSessionMock.mockResolvedValue(session);
        const [unassignedJobRow] = getDbMockTableRows("job");
        if (!unassignedJobRow) throw new Error("Expected job fixture data");
        unassignedJobRow[1] = null;
        setDbMockRows("select", [unassignedJobRow]);

        const response = await unassignedRequest();

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([
            { ...serializedJob, assignedDriverId: null },
        ]);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);

        const { sql, values } = getFirstQuery();
        expect(sql).toContain(
            'from "job" where ("job"."assigned_driver_id" is null)',
        );
        expect(sql).toContain('order by "job"."due_date" asc');
        expect(values).toEqual([]);
    });

    it("returns an empty list when there are no unassigned jobs", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockRows("select", []);

        const response = await unassignedRequest();

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([]);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });

    it("returns 500 when the unassigned job lookup fails", async () => {
        getSessionMock.mockResolvedValue(session);
        dbClientQueryMock.mockRejectedValueOnce(
            new Error("database unavailable"),
        );

        const response = await unassignedRequest();

        expect(response.status).toBe(500);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });
});

describe("GET /jobs/unassigned-reduced", () => {
    it("allows access without a token when JOBTOKEN is not configured", async () => {
        const response = await unassignedReducedRequest();

        expect(response.status).toBe(200);
        expect(getSessionMock).not.toHaveBeenCalled();
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });

    it("returns only the public job fields ordered by due date", async () => {
        const [unassignedJobRow] = getDbMockTableRows("job");
        if (!unassignedJobRow) throw new Error("Expected job fixture data");
        unassignedJobRow[1] = null;
        setDbMockRows("select", [
            [
                unassignedJobRow[0],
                unassignedJobRow[3],
                unassignedJobRow[4],
                unassignedJobRow[5],
                unassignedJobRow[6],
            ],
        ]);

        const response = await unassignedReducedRequest();

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([
            {
                id: serializedJob.id,
                from: serializedJob.from,
                to: serializedJob.to,
                dueDate: serializedJob.dueDate,
                note: serializedJob.note,
            },
        ]);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);

        const { sql, values } = getFirstQuery();
        expect(sql).toContain(
            'select "id", "from"::text, "to"::text, "due_date", "note" from "job"',
        );
        expect(sql).toContain('where ("job"."assigned_driver_id" is null)');
        expect(sql).toContain('order by "job"."due_date" asc');
        const selectClause = sql.split(' from "job"')[0];
        expect(selectClause).not.toContain('"assigned_driver_id"');
        expect(selectClause).not.toContain('"vehicle_id"');
        expect(selectClause).not.toContain('"created_at"');
        expect(selectClause).not.toContain('"updated_at"');
        expect(values).toEqual([]);
    });

    it("allows access when the authorization header token matches JOBTOKEN", async () => {
        envMock.JOBTOKEN = "reduced-jobs-secret";

        const response = await unassignedReducedRequest("reduced-jobs-secret");

        expect(response.status).toBe(200);
        expect(getSessionMock).not.toHaveBeenCalled();
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });

    it.each([
        ["missing", undefined],
        ["incorrect", "wrong-secret"],
        ["empty", ""],
    ])("returns 401 for a %s authorization header token when JOBTOKEN is configured", async (_description, token) => {
        envMock.JOBTOKEN = "reduced-jobs-secret";

        const response = await unassignedReducedRequest(token);

        expect(response.status).toBe(401);
        expect(await response.json()).toEqual({ error: "Unauthorized" });
        expect(getSessionMock).not.toHaveBeenCalled();
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("returns an empty list when there are no unassigned jobs", async () => {
        setDbMockRows("select", []);

        const response = await unassignedReducedRequest();

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([]);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });

    it("returns 500 when the reduced job lookup fails", async () => {
        dbClientQueryMock.mockRejectedValueOnce(
            new Error("database unavailable"),
        );

        const response = await unassignedReducedRequest();

        expect(response.status).toBe(500);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });
});

describe("POST /jobs/create", () => {
    beforeEach(() => {
        setDbMockRows("insert", getDbMockTableRows("job"));
    });

    it("returns 401 without a session and does not create a job", async () => {
        const response = await createRequest(jobBody, false);

        expect(response.status).toBe(401);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("creates a job using every supported field", async () => {
        getSessionMock.mockResolvedValue(session);
        const [createdJobRow] = getDbMockTableRows("job");
        if (!createdJobRow) throw new Error("Expected job fixture data");
        createdJobRow[1] = jobBody.assignedDriverId;
        createdJobRow[2] = jobBody.vehicleId;
        createdJobRow[3] = `(${jobBody.from.join(",")})`;
        createdJobRow[4] = `(${jobBody.to.join(",")})`;
        createdJobRow[5] = jobBody.dueDate.slice(0, -1);
        createdJobRow[6] = jobBody.note;
        setDbMockRows("insert", [createdJobRow]);

        const response = await createRequest(jobBody);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            ...serializedJob,
            assignedDriverId: jobBody.assignedDriverId,
            vehicleId: jobBody.vehicleId,
            from: jobBody.from,
            to: jobBody.to,
            dueDate: jobBody.dueDate,
            note: jobBody.note,
        });
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);

        const { sql, values } = getFirstQuery();
        expect(sql).toContain('insert into "job"');
        expect(sql).toContain("returning");
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
        const [createdJobRow] = getDbMockTableRows("job");
        if (!createdJobRow) throw new Error("Expected job fixture data");
        createdJobRow[1] = null;
        createdJobRow[2] = null;
        createdJobRow[3] = "(0,0)";
        createdJobRow[4] = null;
        createdJobRow[6] = null;
        setDbMockRows("insert", [createdJobRow]);

        const response = await createRequest({ from: [0, 0] });

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            ...serializedJob,
            assignedDriverId: null,
            vehicleId: null,
            from: [0, 0],
            to: null,
            note: null,
        });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);

        const { sql, values } = getFirstQuery();
        expect(sql).toContain(
            "values (default, default, default, $1, default, default, default, default, default, default, default)",
        );
        expect(values).toEqual(["(0,0)"]);
    });

    it("returns 500 when the inserted job is not returned", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockRows("insert", []);

        const response = await createRequest({ from: [0, 0] });

        expect(response.status).toBe(500);
        expect(await response.json()).toEqual({
            error: "Failed to create job",
        });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
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

describe("POST /jobs/:id/assign", () => {
    beforeEach(() => {
        setDbMockRows("update", getDbMockTableRows("job"));
    });

    it("returns 401 without a session and does not update the job", async () => {
        const response = await assignRequest({}, false);

        expect(response.status).toBe(401);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("assigns the job to the authenticated user when the body is omitted", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockRows("update", getDbMockTableRows("job"));

        const response = await assignRequest();

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual(serializedJob);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);

        const { sql, values } = getFirstQuery();
        expect(sql).toContain('update "job" set "assigned_driver_id" = $1');
        expect(sql).toContain('where "job"."id" = $3');
        expect(sql).toContain("returning");
        expect(values[0]).toBe(session.user.id);
        expect(values[1]).toEqual(expect.any(String));
        expect(values[2]).toBe(jobId);
    });

    it("assigns the job to the authenticated user when the body is empty", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await assignRequest({});

        expect(response.status).toBe(200);
        expect(getFirstQuery().values[0]).toBe(session.user.id);
    });

    it("assigns the job to the authenticated user when the body is null", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await assignRequest(null);

        expect(response.status).toBe(200);
        expect(getFirstQuery().values[0]).toBe(session.user.id);
    });

    it("assigns the job to the requested driver", async () => {
        getSessionMock.mockResolvedValue(session);
        const assignedDriverId = "driver-2";

        const response = await assignRequest({ assignedDriverId });

        expect(response.status).toBe(200);
        const { sql, values } = getFirstQuery();
        expect(sql).toContain('"assigned_driver_id" = $1');
        expect(values[0]).toBe(assignedDriverId);
        expect(values.at(-1)).toBe(jobId);
    });

    it("falls back to the authenticated user for a null assigned driver", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await assignRequest({ assignedDriverId: null });

        expect(response.status).toBe(200);
        expect(getFirstQuery().values[0]).toBe(session.user.id);
    });

    it("updates every supported assignment field", async () => {
        getSessionMock.mockResolvedValue(session);
        const assignment = {
            assignedDriverId: "driver-2",
            dueDate: "2026-08-15T14:30:00.000Z",
            to: [48.2101, 16.3645],
        };
        const [updatedJobRow] = getDbMockTableRows("job");
        if (!updatedJobRow) throw new Error("Expected job fixture data");
        updatedJobRow[1] = assignment.assignedDriverId;
        updatedJobRow[4] = `(${assignment.to.join(",")})`;
        updatedJobRow[5] = assignment.dueDate.slice(0, -1);
        setDbMockRows("update", [updatedJobRow]);

        const response = await assignRequest(assignment);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            ...serializedJob,
            assignedDriverId: assignment.assignedDriverId,
            dueDate: assignment.dueDate,
            to: assignment.to,
        });
        const { sql, values } = getFirstQuery();
        expect(sql).toContain('"assigned_driver_id" = $1');
        expect(sql).toContain('"to" = $2');
        expect(sql).toContain('"due_date" = $3');
        expect(values[0]).toBe(assignment.assignedDriverId);
        expect(values[1]).toBe("(48.2101,16.3645)");
        expect(values[2]).toBe(assignment.dueDate);
        expect(values.at(-1)).toBe(jobId);
    });

    it("updates a due date while defaulting the driver to the authenticated user", async () => {
        getSessionMock.mockResolvedValue(session);
        const dueDate = "2026-08-15T14:30:00.000Z";

        const response = await assignRequest({ dueDate });

        expect(response.status).toBe(200);
        const { sql, values } = getFirstQuery();
        expect(sql).toContain('"due_date" = $2');
        expect(values[0]).toBe(session.user.id);
        expect(values[1]).toBe(dueDate);
        expect(values.at(-1)).toBe(jobId);
    });

    it("updates a destination while defaulting the driver to the authenticated user", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await assignRequest({ to: [-90, 180] });

        expect(response.status).toBe(200);
        const { sql, values } = getFirstQuery();
        expect(sql).toContain('"to" = $2');
        expect(values[0]).toBe(session.user.id);
        expect(values[1]).toBe("(-90,180)");
        expect(values.at(-1)).toBe(jobId);
    });

    it("returns 404 when the job does not exist", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockRows("update", []);

        const response = await assignRequest({ assignedDriverId: "driver-2" });

        expect(response.status).toBe(404);
        expect(await response.json()).toEqual({ error: "Job not found" });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });

    it("returns 422 for a non-UUID job id", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await assignRequest({}, true, "not-a-uuid");

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("returns 404 when the job id path segment is missing", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await assignRequest({}, true, "");

        expect(response.status).toBe(404);
        expect(getSessionMock).not.toHaveBeenCalled();
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it.each([
        ["a non-string driver id", { assignedDriverId: 123 }],
        ["an invalid due date", { dueDate: "not-a-date" }],
        ["a null due date", { dueDate: null }],
        ["a destination with too few coordinates", { to: [48.2] }],
        ["a non-numeric destination", { to: ["48.2", 16.3] }],
        ["a destination latitude below -90", { to: [-90.01, 0] }],
        ["a destination latitude above 90", { to: [90.01, 0] }],
        ["a destination longitude below -180", { to: [0, -180.01] }],
        ["a destination longitude above 180", { to: [0, 180.01] }],
        ["a null destination", { to: null }],
    ])("returns 422 for %s", async (_description, body) => {
        getSessionMock.mockResolvedValue(session);

        const response = await assignRequest(body);

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("treats an array body as an empty assignment body", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await assignRequest(["driver-2"]);

        expect(response.status).toBe(200);
        expect(getFirstQuery().values[0]).toBe(session.user.id);
    });

    it("discards fields that are not assignable", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await assignRequest({
            vehicleId: "d6503952-72f5-4b73-a826-e1ab44e0ba72",
            note: "must not be changed",
            startedAt: "2026-08-15T14:30:00.000Z",
        });

        expect(response.status).toBe(200);
        const { sql, values } = getFirstQuery();
        const setClause = sql.split(" returning ")[0];
        expect(setClause).not.toContain('"vehicle_id"');
        expect(setClause).not.toContain('"note"');
        expect(setClause).not.toContain('"started_at"');
        expect(values[0]).toBe(session.user.id);
    });

    it("returns 500 when the job update fails", async () => {
        getSessionMock.mockResolvedValue(session);
        dbClientQueryMock.mockRejectedValueOnce(
            new Error("database unavailable"),
        );

        const response = await assignRequest({ assignedDriverId: "driver-2" });

        expect(response.status).toBe(500);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });
});

describe("POST /jobs/:id/start", () => {
    beforeEach(() => {
        setDbMockRowCount("update", 1);
    });

    it("returns 401 without a session and does not update the job", async () => {
        const response = await mutationRequest("start", {}, false);

        expect(response.status).toBe(401);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("starts a job when the body is omitted", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await mutationRequest("start");

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            message: "Job started successfully",
        });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);

        const { sql, values } = getFirstQuery();
        expect(sql).toContain('update "job" set "started_at" = $1');
        expect(sql).toContain('where "job"."id" = $3');
        expect(values).toHaveLength(3);
        expect(values[0]).toEqual(expect.any(String));
        expect(
            Number.isNaN(new Date(values[0] as string).getTime()),
        ).toBeFalse();
        expect(values[1]).toEqual(expect.any(String));
        expect(values[2]).toBe(jobId);
    });

    it("starts a job with a destination and vehicle", async () => {
        getSessionMock.mockResolvedValue(session);
        const body = {
            to: [-90, 180],
            vehicleId: "d6503952-72f5-4b73-a826-e1ab44e0ba72",
        };

        const response = await mutationRequest("start", body);

        expect(response.status).toBe(200);
        const { sql, values } = getFirstQuery();
        expect(sql).toContain('"vehicle_id" = $1');
        expect(sql).toContain('"to" = $2');
        expect(sql).toContain('"started_at" = $3');
        expect(values[0]).toBe(body.vehicleId);
        expect(values[1]).toBe("(-90,180)");
        expect(values[2]).toEqual(expect.any(String));
        expect(values.at(-1)).toBe(jobId);
    });

    it.each([
        {},
        null,
        { vehicleId: null },
    ])("starts a job without optional changes for body %j", async (body) => {
        getSessionMock.mockResolvedValue(session);

        const response = await mutationRequest("start", body);

        expect(response.status).toBe(200);
        const { sql } = getFirstQuery();
        const setClause = sql.split(" where ")[0];
        expect(setClause).not.toContain('"to"');
        expect(setClause).not.toContain('"vehicle_id"');
    });

    it("discards fields that cannot be changed while starting", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await mutationRequest("start", {
            assignedDriverId: "driver-2",
            dueDate: "2026-08-15T14:30:00.000Z",
            note: "must not be changed",
            completedAt: "2026-08-15T15:30:00.000Z",
        });

        expect(response.status).toBe(200);
        const { sql } = getFirstQuery();
        const setClause = sql.split(" where ")[0];
        expect(setClause).not.toContain('"assigned_driver_id"');
        expect(setClause).not.toContain('"due_date"');
        expect(setClause).not.toContain('"note"');
        expect(setClause).not.toContain('"completed_at"');
    });

    it("returns 404 when the job does not exist", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockRowCount("update", 0);

        const response = await mutationRequest("start");

        expect(response.status).toBe(404);
        expect(await response.json()).toEqual({ error: "Job not found" });
    });

    it.each([
        ["a non-UUID job id", {}, "not-a-uuid"],
        ["an invalid vehicle id", { vehicleId: "not-a-uuid" }, jobId],
        ["a null destination", { to: null }, jobId],
        ["a destination with too few coordinates", { to: [48.2] }, jobId],
        ["a non-numeric destination", { to: ["48.2", 16.3] }, jobId],
        ["a destination latitude below -90", { to: [-90.01, 0] }, jobId],
        ["a destination latitude above 90", { to: [90.01, 0] }, jobId],
        ["a destination longitude below -180", { to: [0, -180.01] }, jobId],
        ["a destination longitude above 180", { to: [0, 180.01] }, jobId],
    ])("returns 422 for %s", async (_description, body, id) => {
        getSessionMock.mockResolvedValue(session);

        const response = await mutationRequest("start", body, true, id);

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("returns 500 when the job update fails", async () => {
        getSessionMock.mockResolvedValue(session);
        dbClientQueryMock.mockRejectedValueOnce(
            new Error("database unavailable"),
        );

        const response = await mutationRequest("start");

        expect(response.status).toBe(500);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });
});

describe("POST /jobs/:id/complete", () => {
    beforeEach(() => {
        setDbMockRowCount("update", 1);
    });

    it("returns 401 without a session and does not update the job", async () => {
        const response = await mutationRequest("complete", undefined, false);

        expect(response.status).toBe(401);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("completes the requested job", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await mutationRequest("complete");

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            message: "Job completed successfully",
        });
        const { sql, values } = getFirstQuery();
        expect(sql).toContain('update "job" set "completed_at" = $1');
        expect(sql).toContain('where "job"."id" = $3');
        expect(values).toHaveLength(3);
        expect(values[0]).toEqual(expect.any(String));
        expect(
            Number.isNaN(new Date(values[0] as string).getTime()),
        ).toBeFalse();
        expect(values[1]).toEqual(expect.any(String));
        expect(values[2]).toBe(jobId);
    });

    it("returns 404 when the job does not exist", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockRowCount("update", 0);

        const response = await mutationRequest("complete");

        expect(response.status).toBe(404);
        expect(await response.json()).toEqual({ error: "Job not found" });
    });

    it("returns 422 for a non-UUID job id", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await mutationRequest(
            "complete",
            undefined,
            true,
            "not-a-uuid",
        );

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("returns 500 when the job update fails", async () => {
        getSessionMock.mockResolvedValue(session);
        dbClientQueryMock.mockRejectedValueOnce(
            new Error("database unavailable"),
        );

        const response = await mutationRequest("complete");

        expect(response.status).toBe(500);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });
});

describe("POST /jobs/:id/cancel", () => {
    beforeEach(() => {
        setDbMockRowCount("update", 1);
    });

    it("returns 401 without a session and does not update the job", async () => {
        const response = await mutationRequest("cancel", undefined, false);

        expect(response.status).toBe(401);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("clears assignment and progress state", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await mutationRequest("cancel");

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            message: "Job canceled successfully",
        });
        const { sql, values } = getFirstQuery();
        expect(sql).toContain('"assigned_driver_id" = $1');
        expect(sql).toContain('"vehicle_id" = $2');
        expect(sql).toContain('"started_at" = $3');
        expect(sql).toContain('"completed_at" = $4');
        expect(values.slice(0, 4)).toEqual([null, null, null, null]);
        expect(values[4]).toEqual(expect.any(String));
        expect(values[5]).toBe(jobId);
    });

    it("returns 404 when the job does not exist", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockRowCount("update", 0);

        const response = await mutationRequest("cancel");

        expect(response.status).toBe(404);
        expect(await response.json()).toEqual({ error: "Job not found" });
    });

    it("returns 422 for a non-UUID job id", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await mutationRequest(
            "cancel",
            undefined,
            true,
            "not-a-uuid",
        );

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("returns 500 when the job update fails", async () => {
        getSessionMock.mockResolvedValue(session);
        dbClientQueryMock.mockRejectedValueOnce(
            new Error("database unavailable"),
        );

        const response = await mutationRequest("cancel");

        expect(response.status).toBe(500);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });
});

describe("PUT /jobs/:id", () => {
    beforeEach(() => {
        setDbMockRowCount("update", 1);
    });

    it("returns 401 without a session and does not update the job", async () => {
        const response = await updateRequest({}, false);

        expect(response.status).toBe(401);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("updates every supported job field", async () => {
        getSessionMock.mockResolvedValue(session);
        const body = {
            assignedDriverId: "driver-2",
            vehicleId: "d6503952-72f5-4b73-a826-e1ab44e0ba72",
            from: [48.2082, 16.3738],
            to: [48.1947, 16.3122],
            dueDate: "2026-08-15T14:30:00.000Z",
            note: "Updated delivery",
            startedAt: "2026-08-15T13:30:00.000Z",
            completedAt: "2026-08-15T15:30:00.000Z",
        };

        const response = await updateRequest(body);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            message: "Job updated successfully",
        });
        const { sql, values } = getFirstQuery();
        expect(sql).toContain('update "job" set');
        expect(sql).toContain('"assigned_driver_id" = $1');
        expect(sql).toContain('"vehicle_id" = $2');
        expect(sql).toContain('"from" = $3');
        expect(sql).toContain('"to" = $4');
        expect(sql).toContain('"due_date" = $5');
        expect(sql).toContain('"note" = $6');
        expect(sql).toContain('"started_at" = $7');
        expect(sql).toContain('"completed_at" = $8');
        expect(values.slice(0, 8)).toEqual([
            body.assignedDriverId,
            body.vehicleId,
            "(48.2082,16.3738)",
            "(48.1947,16.3122)",
            body.dueDate,
            body.note,
            body.startedAt,
            body.completedAt,
        ]);
        expect(values[8]).toEqual(expect.any(String));
        expect(values[9]).toBe(jobId);
    });

    it("updates only the supplied fields", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await updateRequest({ note: "Leave at reception" });

        expect(response.status).toBe(200);
        const { sql, values } = getFirstQuery();
        expect(sql).toContain(
            'set "note" = $1, "updated_at" = $2 where "job"."id" = $3',
        );
        expect(values[0]).toBe("Leave at reception");
        expect(values[1]).toEqual(expect.any(String));
        expect(values[2]).toBe(jobId);
    });

    it("accepts nullable job fields", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await updateRequest({
            assignedDriverId: null,
            vehicleId: null,
            note: null,
            startedAt: null,
            completedAt: null,
        });

        expect(response.status).toBe(200);
        const { values } = getFirstQuery();
        expect(values.slice(0, 5)).toEqual([null, null, null, null, null]);
        expect(values.at(-1)).toBe(jobId);
    });

    it("returns 500 for an empty update because there are no values to set", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await updateRequest({});

        expect(response.status).toBe(500);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("discards server-managed and unknown fields", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await updateRequest({
            note: "Allowed",
            id: "e65df7c8-fcea-47bc-83b8-058817832952",
            createdAt: "2020-01-01T00:00:00.000Z",
            updatedAt: "2020-01-01T00:00:00.000Z",
            unexpected: "value",
        });

        expect(response.status).toBe(200);
        const { sql, values } = getFirstQuery();
        const setClause = sql.split(" where ")[0];
        expect(setClause).not.toContain('"id"');
        expect(setClause).not.toContain('"created_at"');
        expect(setClause).not.toContain('"unexpected"');
        expect(values[0]).toBe("Allowed");
        expect(values).not.toContain("2020-01-01T00:00:00.000Z");
    });

    it("returns 404 when the job does not exist", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockRowCount("update", 0);

        const response = await updateRequest({ note: "Updated" });

        expect(response.status).toBe(404);
        expect(await response.json()).toEqual({ error: "Job not found" });
    });

    it.each([
        ["a non-UUID job id", { note: "Updated" }, "not-a-uuid"],
        ["a null origin", { from: null }, jobId],
        ["an origin with too few coordinates", { from: [48.2] }, jobId],
        ["a non-numeric origin", { from: ["48.2", 16.3] }, jobId],
        ["an origin latitude below -90", { from: [-90.01, 0] }, jobId],
        ["an origin longitude above 180", { from: [0, 180.01] }, jobId],
        ["a destination with too few coordinates", { to: [48.2] }, jobId],
        ["a destination latitude above 90", { to: [90.01, 0] }, jobId],
        ["a null destination", { to: null }, jobId],
        ["an invalid vehicle id", { vehicleId: "not-a-uuid" }, jobId],
        ["an invalid due date", { dueDate: "not-a-date" }, jobId],
        ["a null due date", { dueDate: null }, jobId],
        ["a non-string note", { note: 123 }, jobId],
        ["an invalid started date", { startedAt: "not-a-date" }, jobId],
        ["an invalid completed date", { completedAt: "not-a-date" }, jobId],
    ])("returns 422 for %s", async (_description, body, id) => {
        getSessionMock.mockResolvedValue(session);

        const response = await updateRequest(body, true, id);

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("returns 500 when the job update fails", async () => {
        getSessionMock.mockResolvedValue(session);
        dbClientQueryMock.mockRejectedValueOnce(
            new Error("database unavailable"),
        );

        const response = await updateRequest({ note: "Updated" });

        expect(response.status).toBe(500);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });
});

describe("GET /jobs/:id", () => {
    it("returns 401 without a session and does not query the database", async () => {
        const response = await getJobRequest(false);

        expect(response.status).toBe(401);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("returns the requested job", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await getJobRequest();

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual(serializedJob);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
        const { sql, values } = getFirstQuery();
        expect(sql).toContain('from "job" where "job"."id" = $1 limit $2');
        expect(values).toEqual([jobId, 1]);
    });

    it("returns 404 when the job does not exist", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockRows("select", []);

        const response = await getJobRequest();

        expect(response.status).toBe(404);
        expect(await response.json()).toEqual({ error: "Job not found" });
    });

    it("returns 422 for a non-UUID job id", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await getJobRequest(true, "not-a-uuid");

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("returns 500 when the job lookup fails", async () => {
        getSessionMock.mockResolvedValue(session);
        dbClientQueryMock.mockRejectedValueOnce(
            new Error("database unavailable"),
        );

        const response = await getJobRequest();

        expect(response.status).toBe(500);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });
});
