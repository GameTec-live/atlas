import { afterAll, beforeEach, describe, expect, it, mock } from "bun:test";
import { Elysia } from "elysia";
import { getSessionMock, resetAuthMocks, session } from "../mocks/auth";
import {
    dbClientQueryMock,
    exampleData,
    getDbMockTableRows,
    resetDbMocks,
    setDbMockRowCount,
    setDbMockRows,
    setDbMockTableRows,
} from "../mocks/db";

const envMock: {
    GEOCODER_URL: string;
    JOBTOKEN?: string;
    ROUTER_URL: string;
} = {
    GEOCODER_URL: "http://geocoder.test",
    ROUTER_URL: "http://router.test",
};

mock.module("@/env", () => ({
    env: envMock,
}));

const { jobs } = await import("@/src/jobs");
const {
    NOTIFICATION_ADDRESS_MAX_LENGTH,
    notifyAssignedDriverInBackground,
    sendAssignmentNotification,
    shortenAddress,
} = await import("@/src/jobs/notifications");
const { trackCache } = await import("@/src/realtime");
const app = new Elysia().use(jobs);

const originalFetch = globalThis.fetch;
const fetchMock = mock(
    async (
        _input: string | URL | Request,
        _init?: RequestInit,
    ): Promise<Response> => {
        throw new Error("Unexpected routing request");
    },
);

const jobBody = {
    assignedDriverId: "user-1",
    vehicleId: "7bb0de4d-bcdd-4c99-a852-a17a4bbdb3de",
    from: [48.2082, 16.3738],
    to: [48.1947, 16.3122],
    dueDate: "2026-08-01T12:00:00.000Z",
    note: "Deliver package to destination",
};

const request = (authenticated = true, geocode = false) => {
    const headers = new Headers();
    if (authenticated) headers.set("authorization", "Bearer test-token");
    const url = new URL("http://localhost/jobs/assigned");
    if (geocode) url.searchParams.set("geocode", "");

    return app.handle(new Request(url.toString(), { headers }));
};

const currentRequest = (authenticated = true, geocode = false) => {
    const headers = new Headers();
    if (authenticated) headers.set("authorization", "Bearer test-token");
    const url = new URL("http://localhost/jobs/current");
    if (geocode) url.searchParams.set("geocode", "");

    return app.handle(new Request(url.toString(), { headers }));
};

const unassignedRequest = (authenticated = true, geocode = false) => {
    const headers = new Headers();
    if (authenticated) headers.set("authorization", "Bearer test-token");
    const url = new URL("http://localhost/jobs/unassigned");
    if (geocode) url.searchParams.set("geocode", "");

    return app.handle(new Request(url.toString(), { headers }));
};

const unassignedReducedRequest = (token?: string, geocode = false) => {
    const headers = new Headers();
    if (token !== undefined) {
        headers.set("authorization", token);
    }
    const url = new URL("http://localhost/jobs/unassigned-reduced");
    if (geocode) url.searchParams.set("geocode", "");

    return app.handle(new Request(url.toString(), { headers }));
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

const deleteJobRequest = (authenticated = true, id = jobId) => {
    const headers = new Headers();
    if (authenticated) headers.set("authorization", "Bearer test-token");

    return app.handle(
        new Request(`http://localhost/jobs/${id}`, {
            method: "DELETE",
            headers,
        }),
    );
};

const getJobRequest = (authenticated = true, id = jobId, geocode = false) => {
    const headers = new Headers();
    if (authenticated) headers.set("authorization", "Bearer test-token");
    const url = new URL(`http://localhost/jobs/${id}`);
    if (geocode) url.searchParams.set("geocode", "");

    return app.handle(new Request(url.toString(), { headers }));
};

const allJobsRequest = (
    filter?: string,
    authenticated = true,
    geocode = false,
) => {
    const headers = new Headers();
    if (authenticated) headers.set("authorization", "Bearer test-token");

    const url = new URL("http://localhost/jobs/all");
    if (filter !== undefined) url.searchParams.set("filter", filter);
    if (geocode) url.searchParams.set("geocode", "");

    return app.handle(new Request(url.toString(), { headers }));
};

const candidatesRequest = (authenticated = true, id = jobId) => {
    const headers = new Headers();
    if (authenticated) headers.set("authorization", "Bearer test-token");

    return app.handle(
        new Request(`http://localhost/jobs/${id}/candidates`, { headers }),
    );
};

const adhocCandidatesRequest = (body: unknown, authenticated = true) => {
    const headers = new Headers({ "content-type": "application/json" });
    if (authenticated) headers.set("authorization", "Bearer test-token");

    return app.handle(
        new Request("http://localhost/jobs/candidates", {
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

const addressForCoordinates = ([latitude, longitude]: [number, number]) =>
    `Address at ${latitude}, ${longitude}`;

const serializedJobWithAddresses = {
    ...serializedJob,
    fromAddress: addressForCoordinates(serializedJob.from),
    toAddress: serializedJob.to
        ? addressForCoordinates(serializedJob.to)
        : null,
};

const adminSession = {
    ...session,
    user: {
        ...session.user,
        role: "admin",
    },
};

beforeEach(() => {
    envMock.JOBTOKEN = undefined;
    resetAuthMocks();
    resetDbMocks();
    trackCache.clear();
    fetchMock.mockReset();
    fetchMock.mockImplementation(async (input) => {
        const url = new URL(String(input));
        if (url.pathname !== "/reverse") {
            throw new Error("Unexpected routing request");
        }

        const latitude = Number(url.searchParams.get("lat"));
        const longitude = Number(url.searchParams.get("lon"));
        return Response.json({
            count: 1,
            results: [
                {
                    pack: "austria.sqlite",
                    kind: "address",
                    lat: latitude,
                    lon: longitude,
                    display_name: addressForCoordinates([latitude, longitude]),
                },
            ],
        });
    });
    globalThis.fetch = fetchMock as unknown as typeof fetch;
});

afterAll(() => {
    globalThis.fetch = originalFetch;
});

describe("GET /jobs/assigned", () => {
    it("returns 401 without a session and does not query the database", async () => {
        const response = await request(false);

        expect(response.status).toBe(401);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("returns the authenticated user's incomplete assigned jobs without geocoding by default", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await request();

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([serializedJob]);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
        expect(fetchMock).not.toHaveBeenCalled();

        const { sql, values } = getFirstQuery();
        expect(sql).toContain('"job"."assigned_driver_id" = $1');
        expect(sql).toContain('"job"."completed_at" is null');
        expect(values).toEqual([session.user.id]);
    });

    it("enriches assigned jobs when ?geocode is present", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await request(true, true);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([serializedJobWithAddresses]);
        expect(fetchMock.mock.calls.map(([input]) => String(input))).toEqual([
            `${envMock.GEOCODER_URL}/reverse?lat=${serializedJob.from[0]}&lon=${serializedJob.from[1]}&limit=1`,
            `${envMock.GEOCODER_URL}/reverse?lat=${serializedJob.to?.[0]}&lon=${serializedJob.to?.[1]}&limit=1`,
        ]);
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
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it("de-duplicates shared coordinates while enriching a job list", async () => {
        getSessionMock.mockResolvedValue(session);
        const [firstJobRow] = getDbMockTableRows("job");
        if (!firstJobRow) throw new Error("Expected job fixture data");
        const secondJobRow = [...firstJobRow];
        secondJobRow[0] = "411ba9ee-e2aa-42c4-880f-8cda75d2e6ad";
        setDbMockRows("select", [firstJobRow, secondJobRow]);

        const response = await request(true, true);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([
            serializedJobWithAddresses,
            {
                ...serializedJobWithAddresses,
                id: secondJobRow[0],
            },
        ]);
        expect(fetchMock).toHaveBeenCalledTimes(2);
    });

    it("returns jobs and omits only an address whose geocoder request fails", async () => {
        getSessionMock.mockResolvedValue(session);
        fetchMock.mockRejectedValueOnce(new Error("geocoder unavailable"));

        const response = await request(true, true);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([
            {
                ...serializedJob,
                toAddress: serializedJobWithAddresses.toAddress,
            },
        ]);
        expect(fetchMock).toHaveBeenCalledTimes(2);
    });

    it("returns jobs and omits only an address from a malformed geocoder response", async () => {
        getSessionMock.mockResolvedValue(session);
        fetchMock.mockResolvedValueOnce(Response.json({ invalid: true }));

        const response = await request(true, true);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([
            {
                ...serializedJob,
                toAddress: serializedJobWithAddresses.toAddress,
            },
        ]);
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

describe("GET /jobs/current", () => {
    it("returns 401 without a session and does not query the database", async () => {
        const response = await currentRequest(false);

        expect(response.status).toBe(401);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("returns the authenticated user's most recently started incomplete job without geocoding by default", async () => {
        getSessionMock.mockResolvedValue(session);
        const [currentJobRow] = getDbMockTableRows("job");
        if (!currentJobRow) throw new Error("Expected job fixture data");
        const startedAt = "2026-08-11T08:30:00.000Z";
        currentJobRow[7] = startedAt.slice(0, -1);
        setDbMockRows("select", [currentJobRow]);

        const response = await currentRequest();

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            ...serializedJob,
            startedAt,
        });
        expect(fetchMock).not.toHaveBeenCalled();
        const { sql, values } = getFirstQuery();
        expect(sql).toContain('"job"."assigned_driver_id" = $1');
        expect(sql).toContain('"job"."started_at" is not null');
        expect(sql).toContain('"job"."completed_at" is null');
        expect(sql).toContain('order by "job"."started_at" desc limit $2');
        expect(values).toEqual([session.user.id, 1]);
    });

    it("enriches the current job when ?geocode is present", async () => {
        getSessionMock.mockResolvedValue(session);
        const [currentJobRow] = getDbMockTableRows("job");
        if (!currentJobRow) throw new Error("Expected job fixture data");
        const startedAt = "2026-08-11T08:30:00.000Z";
        currentJobRow[7] = startedAt.slice(0, -1);
        setDbMockRows("select", [currentJobRow]);

        const response = await currentRequest(true, true);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            ...serializedJobWithAddresses,
            startedAt,
        });
        expect(fetchMock).toHaveBeenCalledTimes(2);
    });

    it("returns 404 when the user has no started job", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockRows("select", []);

        const response = await currentRequest();

        expect(response.status).toBe(404);
        expect(await response.json()).toEqual({ error: "No current job" });
    });

    it("returns 500 when the current job lookup fails", async () => {
        getSessionMock.mockResolvedValue(session);
        dbClientQueryMock.mockRejectedValueOnce(
            new Error("database unavailable"),
        );

        const response = await currentRequest();

        expect(response.status).toBe(500);
    });
});

describe("GET /jobs/unassigned", () => {
    it("returns 401 without a session and does not query the database", async () => {
        const response = await unassignedRequest(false);

        expect(response.status).toBe(401);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("returns complete unassigned jobs without geocoding by default", async () => {
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
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it("enriches unassigned jobs when ?geocode is present", async () => {
        getSessionMock.mockResolvedValue(session);
        const [unassignedJobRow] = getDbMockTableRows("job");
        if (!unassignedJobRow) throw new Error("Expected job fixture data");
        unassignedJobRow[1] = null;
        setDbMockRows("select", [unassignedJobRow]);

        const response = await unassignedRequest(true, true);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([
            { ...serializedJobWithAddresses, assignedDriverId: null },
        ]);
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
        setDbMockRows("select", [
            [
                serializedJob.id,
                `(${serializedJob.from.join(",")})`,
                serializedJob.to ? `(${serializedJob.to.join(",")})` : null,
                serializedJob.dueDate.slice(0, -1),
                serializedJob.note,
            ],
        ]);

        const response = await unassignedReducedRequest();

        expect(response.status).toBe(200);
        expect(getSessionMock).not.toHaveBeenCalled();
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
        expect(fetchMock).not.toHaveBeenCalled();
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
        expect(fetchMock).not.toHaveBeenCalled();

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

    it("enriches reduced jobs when ?geocode is present", async () => {
        const [unassignedJobRow] = getDbMockTableRows("job");
        if (!unassignedJobRow) throw new Error("Expected job fixture data");
        setDbMockRows("select", [
            [
                unassignedJobRow[0],
                unassignedJobRow[3],
                unassignedJobRow[4],
                unassignedJobRow[5],
                unassignedJobRow[6],
            ],
        ]);

        const response = await unassignedReducedRequest(undefined, true);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([
            {
                id: serializedJob.id,
                from: serializedJob.from,
                to: serializedJob.to,
                dueDate: serializedJob.dueDate,
                note: serializedJob.note,
                fromAddress: serializedJobWithAddresses.fromAddress,
                toAddress: serializedJobWithAddresses.toAddress,
            },
        ]);
        expect(fetchMock).toHaveBeenCalledTimes(2);
    });

    it("allows access when the authorization header token matches JOBTOKEN", async () => {
        envMock.JOBTOKEN = "reduced-jobs-secret";
        setDbMockRows("select", [
            [
                serializedJob.id,
                `(${serializedJob.from.join(",")})`,
                serializedJob.to ? `(${serializedJob.to.join(",")})` : null,
                serializedJob.dueDate.slice(0, -1),
                serializedJob.note,
            ],
        ]);

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

describe("job candidate endpoints", () => {
    const currentJobId = "411ba9ee-e2aa-42c4-880f-8cda75d2e6ad";
    type CandidateResponse = {
        driverName: string;
        state: string;
        currentJobId?: string;
        precedingJobIds: string[];
        followingJobs: unknown[];
        maximumFollowingLatenessSeconds: number;
        routeDurationSeconds: number;
        rankingTrace: {
            rank: number;
            summaryCode: string;
            summaryValues: Record<string, unknown>;
            summary: string;
            comparedTo?: {
                driverId: string;
                driverName: string;
                relation: "ahead" | "behind" | "tied";
            };
            decisiveCriterion?: string;
            steps: {
                criterion: string;
                outcome: "better" | "equal" | "worse";
                code: string;
                values: {
                    candidate: boolean | number | string;
                    comparedTo: boolean | number | string;
                    unit: "boolean" | "seconds" | "dateTime";
                };
                message: string;
            }[];
        };
    };
    type CandidateRecord = Record<string, unknown> &
        Pick<CandidateResponse, "rankingTrace">;

    const setDriverUsers = (...drivers: [id: string, name: string][]) => {
        const [exampleUserRow] = getDbMockTableRows("user");
        if (!exampleUserRow) throw new Error("Expected user fixture data");

        setDbMockTableRows(
            "user",
            drivers.map(([id, name]) => {
                const userRow = [...exampleUserRow];
                userRow[0] = id;
                userRow[1] = name;
                return userRow;
            }),
        );
    };

    const setTargetAndCurrentJobRows = (
        assignedDriverId: string | null,
        dueDate = "2099-08-05T12:00:00.000",
        currentDueDate = "2026-08-05T09:00:00.000",
        currentStartedAt: string | null = null,
    ) => {
        const [targetRow] = getDbMockTableRows("job");
        if (!targetRow) throw new Error("Expected job fixture data");

        targetRow[1] = assignedDriverId;
        targetRow[3] = "(48.5,16.6)";
        targetRow[4] = "(48.6,16.7)";
        targetRow[5] = dueDate;
        targetRow[7] = null;
        targetRow[8] = null;

        const currentRow = [...targetRow];
        currentRow[0] = currentJobId;
        currentRow[1] = "busy-driver";
        currentRow[3] = "(48.3,16.4)";
        currentRow[4] = "(48.4,16.5)";
        currentRow[5] = currentDueDate;
        currentRow[7] = currentStartedAt;

        setDbMockTableRows("job", [targetRow, currentRow]);
    };

    const routeResponse = (
        points: { lat: number; lon: number }[],
        times: number[],
    ) => {
        const summary = (time: number) => ({
            has_time_restrictions: false,
            has_toll: false,
            has_highway: false,
            has_ferry: false,
            min_lat: Math.min(...points.map(({ lat }) => lat)),
            min_lon: Math.min(...points.map(({ lon }) => lon)),
            max_lat: Math.max(...points.map(({ lat }) => lat)),
            max_lon: Math.max(...points.map(({ lon }) => lon)),
            time,
            length: time / 100,
            cost: time,
        });
        const totalTime = times.reduce((total, time) => total + time, 0);

        return {
            trip: {
                locations: points.map((point, index) => ({
                    type: "break",
                    ...point,
                    original_index: index,
                })),
                legs: times.map((time) => ({
                    summary: summary(time),
                    shape: "encoded-route-shape",
                })),
                summary: summary(totalTime),
                status_message: "Found route between points",
                status: 0,
                units: "kilometers",
                language: "en-US",
            },
        };
    };

    it("POST /jobs/candidates requires authentication", async () => {
        const response = await adhocCandidatesRequest(
            { from: [48.5, 16.6] },
            false,
        );

        expect(response.status).toBe(401);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it("POST /jobs/candidates validates the ad-hoc job", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await adhocCandidatesRequest({
            dueDate: "2099-08-05T12:00:00.000Z",
        });

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it("POST /jobs/candidates defaults an omitted due date to now", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await adhocCandidatesRequest({
            from: [48.5, 16.6],
        });

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([]);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it("POST /jobs/candidates calculates candidates without loading a target job", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockTableRows("job", []);
        trackCache.set("user-1", {
            type: "update",
            latitude: 48.1,
            longitude: 16.2,
            state: "free",
        });
        fetchMock.mockImplementation(async (input) => {
            const routerUrl = new URL(String(input));
            const query = JSON.parse(routerUrl.searchParams.get("json") ?? "");
            const points = query.locations.map(
                ({ lat, lon }: { lat: number; lon: number }) => ({ lat, lon }),
            );

            return Response.json(routeResponse(points, [300]));
        });

        const response = await adhocCandidatesRequest({
            from: [48.5, 16.6],
            to: [48.6, 16.7],
            dueDate: "2099-08-05T12:00:00.000Z",
        });
        const body = (await response.json()) as CandidateRecord[];

        expect(response.status).toBe(200);
        expect(body.map(({ driverId }) => driverId)).toEqual(["user-1"]);
        expect(body.map(({ driverName }) => driverName)).toEqual([
            "Test Driver",
        ]);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(2);
        const { sql } = getFirstQuery();
        expect(sql).toContain('"assigned_driver_id" in ($1)');
        expect(sql).toContain('"completed_at" is null');

        const routerUrl = new URL(String(fetchMock.mock.calls[0]?.[0]));
        const route = JSON.parse(routerUrl.searchParams.get("json") ?? "");
        expect(
            route.locations.map(
                ({ lat, lon }: { lat: number; lon: number }) => [lat, lon],
            ),
        ).toEqual([
            [48.1, 16.2],
            [48.5, 16.6],
        ]);
    });

    it("returns 401 without a session", async () => {
        const response = await candidatesRequest(false);

        expect(response.status).toBe(401);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it("returns 404 when the job does not exist", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockRows("select", []);

        const response = await candidatesRequest();

        expect(response.status).toBe(404);
        expect(await response.json()).toEqual({ error: "Job not found" });
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it("rejects candidate calculation for an already assigned job", async () => {
        getSessionMock.mockResolvedValue(session);
        setTargetAndCurrentJobRows("existing-driver");

        const response = await candidatesRequest();

        expect(response.status).toBe(409);
        expect(await response.json()).toEqual({
            error: "Job is already assigned",
        });
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it("ranks on-time drivers by final approach and routes earlier backlog first", async () => {
        getSessionMock.mockResolvedValue(session);
        setTargetAndCurrentJobRows(null);
        setDriverUsers(
            ["busy-driver", "Busy Driver"],
            ["free-driver", "Free Driver"],
        );
        trackCache.set("busy-driver", {
            type: "update",
            latitude: 48.2,
            longitude: 16.3,
            state: "free",
        });
        trackCache.set("free-driver", {
            type: "update",
            latitude: 48.1,
            longitude: 16.2,
            state: "free",
        });
        trackCache.set("away-driver", {
            type: "update",
            latitude: 48,
            longitude: 16.1,
            state: "away",
        });
        trackCache.set("unpredictable-driver", {
            type: "update",
            latitude: 48.6,
            longitude: 16.7,
            state: "occupied",
        });
        fetchMock.mockImplementation(async (input) => {
            const routerUrl = new URL(String(input));
            const query = JSON.parse(routerUrl.searchParams.get("json") ?? "");
            const points = query.locations.map(
                ({ lat, lon }: { lat: number; lon: number }) => ({ lat, lon }),
            );
            const times = points.length === 4 ? [400, 450, 50] : [300];

            return Response.json(routeResponse(points, times));
        });

        const response = await candidatesRequest();
        const body = (await response.json()) as CandidateRecord[];

        expect(response.status).toBe(200);
        expect(body).toHaveLength(2);
        expect(body.map(({ driverId }) => driverId)).toEqual([
            "busy-driver",
            "free-driver",
        ]);
        expect(body[0]).toEqual({
            driverId: "busy-driver",
            driverName: "Busy Driver",
            state: "free",
            latitude: 48.2,
            longitude: 16.3,
            precedingJobIds: [currentJobId],
            followingJobs: [],
            estimatedArrivalAt: expect.any(String),
            estimatedPickupAt: expect.any(String),
            routeDurationSeconds: 900,
            waitingDurationSeconds: expect.any(Number),
            routeDistanceKilometers: 9,
            approachDurationSeconds: 50,
            approachDistanceKilometers: 0.5,
            lateBySeconds: 0,
            maximumFollowingLatenessSeconds: 0,
            rankingTrace: expect.any(Object),
        });
        expect(body[1]).toEqual({
            driverId: "free-driver",
            driverName: "Free Driver",
            state: "free",
            latitude: 48.1,
            longitude: 16.2,
            precedingJobIds: [],
            followingJobs: [],
            estimatedArrivalAt: expect.any(String),
            estimatedPickupAt: expect.any(String),
            routeDurationSeconds: 300,
            waitingDurationSeconds: expect.any(Number),
            routeDistanceKilometers: 3,
            approachDurationSeconds: 300,
            approachDistanceKilometers: 3,
            lateBySeconds: 0,
            maximumFollowingLatenessSeconds: 0,
            rankingTrace: expect.any(Object),
        });
        expect(body[0]?.rankingTrace).toEqual({
            rank: 1,
            summaryCode: "rankedAhead",
            summaryValues: {
                rank: 1,
                comparedToDriverId: "free-driver",
                comparedToDriverName: "Free Driver",
                decisiveCriterion: "approachDuration",
            },
            summary:
                "Ranked ahead Free Driver. Final approach takes 50 seconds, shorter than Free Driver at 300 seconds.",
            comparedTo: {
                driverId: "free-driver",
                driverName: "Free Driver",
                relation: "ahead",
            },
            decisiveCriterion: "approachDuration",
            steps: [
                {
                    criterion: "followingJobDisruption",
                    outcome: "equal",
                    code: "followingJobDisruption.equal",
                    values: {
                        candidate: false,
                        comparedTo: false,
                        unit: "boolean",
                    },
                    message:
                        "Like Free Driver, keeps all following jobs on time.",
                },
                {
                    criterion: "maximumFollowingLateness",
                    outcome: "equal",
                    code: "maximumFollowingLateness.equal",
                    values: {
                        candidate: 0,
                        comparedTo: 0,
                        unit: "seconds",
                    },
                    message:
                        "Worst following-job delay matches Free Driver at 0 seconds.",
                },
                {
                    criterion: "targetLateness",
                    outcome: "equal",
                    code: "targetLateness.equal",
                    values: {
                        candidate: false,
                        comparedTo: false,
                        unit: "boolean",
                    },
                    message:
                        "Like Free Driver, can pick up the target job on time.",
                },
                {
                    criterion: "approachDuration",
                    outcome: "better",
                    code: "approachDuration.better",
                    values: {
                        candidate: 50,
                        comparedTo: 300,
                        unit: "seconds",
                    },
                    message:
                        "Final approach takes 50 seconds, shorter than Free Driver at 300 seconds.",
                },
            ],
        });
        expect(body[1]?.rankingTrace).toMatchObject({
            rank: 2,
            comparedTo: {
                driverId: "busy-driver",
                driverName: "Busy Driver",
                relation: "behind",
            },
            decisiveCriterion: "approachDuration",
            steps: expect.arrayContaining([
                expect.objectContaining({
                    criterion: "approachDuration",
                    outcome: "worse",
                }),
            ]),
        });
        expect(fetchMock).toHaveBeenCalledTimes(2);

        const busyRouteUrl = new URL(String(fetchMock.mock.calls[0]?.[0]));
        const busyRoute = JSON.parse(
            busyRouteUrl.searchParams.get("json") ?? "",
        );
        expect(
            busyRoute.locations.map(
                ({ lat, lon }: { lat: number; lon: number }) => [lat, lon],
            ),
        ).toEqual([
            [48.2, 16.3],
            [48.3, 16.4],
            [48.4, 16.5],
            [48.5, 16.6],
        ]);
    });

    it.each([
        [
            "onTheWay" as const,
            [
                [48.2, 16.3],
                [48.3, 16.4],
                [48.4, 16.5],
                [48.5, 16.6],
            ],
        ],
        [
            "occupied" as const,
            [
                [48.2, 16.3],
                [48.4, 16.5],
                [48.5, 16.6],
            ],
        ],
    ])("uses telemetry state %s for a started job", async (state, expectedPoints) => {
        getSessionMock.mockResolvedValue(session);
        setTargetAndCurrentJobRows(
            null,
            "2099-08-05T12:00:00.000",
            "2026-08-05T09:00:00.000",
            "2026-08-05T08:55:00.000",
        );
        setDriverUsers(["busy-driver", "Busy Driver"]);
        trackCache.set("busy-driver", {
            type: "update",
            latitude: 48.2,
            longitude: 16.3,
            state,
        });
        fetchMock.mockImplementation(async (input) => {
            const routerUrl = new URL(String(input));
            const query = JSON.parse(routerUrl.searchParams.get("json") ?? "");
            const points = query.locations.map(
                ({ lat, lon }: { lat: number; lon: number }) => ({ lat, lon }),
            );

            return Response.json(
                routeResponse(
                    points,
                    Array.from({ length: points.length - 1 }, () => 100),
                ),
            );
        });

        const response = await candidatesRequest();
        const [candidate] = (await response.json()) as CandidateResponse[];

        expect(response.status).toBe(200);
        expect(candidate?.state).toBe(state);
        expect(candidate?.driverName).toBe("Busy Driver");
        expect(candidate?.currentJobId).toBe(currentJobId);
        expect(candidate?.precedingJobIds).toEqual([currentJobId]);
        expect(candidate?.rankingTrace).toEqual({
            rank: 1,
            summaryCode: "onlyEligibleDriver",
            summaryValues: { rank: 1 },
            summary: "Only eligible driver.",
            steps: [],
        });

        const routerUrl = new URL(String(fetchMock.mock.calls[0]?.[0]));
        const route = JSON.parse(routerUrl.searchParams.get("json") ?? "");
        expect(
            route.locations.map(
                ({ lat, lon }: { lat: number; lon: number }) => [lat, lon],
            ),
        ).toEqual(expectedPoints);
    });

    it("inserts a sooner job before later backlog and verifies the later pickup", async () => {
        getSessionMock.mockResolvedValue(session);
        setTargetAndCurrentJobRows(
            null,
            "2099-08-05T10:10:00.000",
            "2099-08-05T11:00:00.000",
        );
        setDriverUsers(["busy-driver", "Busy Driver"]);
        trackCache.set("busy-driver", {
            type: "update",
            latitude: 48.2,
            longitude: 16.3,
            state: "free",
        });
        fetchMock.mockImplementation(async (input) => {
            const routerUrl = new URL(String(input));
            const query = JSON.parse(routerUrl.searchParams.get("json") ?? "");
            const points = query.locations.map(
                ({ lat, lon }: { lat: number; lon: number }) => ({ lat, lon }),
            );

            return Response.json(routeResponse(points, [300, 600, 300]));
        });

        const response = await candidatesRequest();
        const [candidate] = (await response.json()) as CandidateResponse[];

        expect(response.status).toBe(200);
        expect(candidate?.precedingJobIds).toEqual([]);
        expect(candidate?.followingJobs).toEqual([
            {
                jobId: currentJobId,
                estimatedPickupAt: "2099-08-05T11:00:00.000Z",
                lateBySeconds: 0,
            },
        ]);
        expect(candidate?.maximumFollowingLatenessSeconds).toBe(0);
        expect(candidate?.routeDurationSeconds).toBe(300);

        const routerUrl = new URL(String(fetchMock.mock.calls[0]?.[0]));
        const route = JSON.parse(routerUrl.searchParams.get("json") ?? "");
        expect(
            route.locations.map(
                ({ lat, lon }: { lat: number; lon: number }) => [lat, lon],
            ),
        ).toEqual([
            [48.2, 16.3],
            [48.5, 16.6],
            [48.6, 16.7],
            [48.3, 16.4],
        ]);
    });

    it("ranks immediate jobs by the earliest pickup time", async () => {
        getSessionMock.mockResolvedValue(session);
        setTargetAndCurrentJobRows(null, "2020-08-05T12:00:00.000");
        setDriverUsers(
            ["slow-driver", "Slow Driver"],
            ["fast-driver", "Fast Driver"],
        );
        trackCache.set("slow-driver", {
            type: "update",
            latitude: 48.1,
            longitude: 16.2,
            state: "free",
        });
        trackCache.set("fast-driver", {
            type: "update",
            latitude: 48.2,
            longitude: 16.3,
            state: "free",
        });
        fetchMock.mockImplementation(async (input) => {
            const routerUrl = new URL(String(input));
            const query = JSON.parse(routerUrl.searchParams.get("json") ?? "");
            const points = query.locations.map(
                ({ lat, lon }: { lat: number; lon: number }) => ({ lat, lon }),
            );
            const duration = points[0]?.lat === 48.2 ? 100 : 500;

            return Response.json(routeResponse(points, [duration]));
        });

        const response = await candidatesRequest();
        const body = (await response.json()) as CandidateRecord[];

        expect(response.status).toBe(200);
        expect(body.map(({ driverId }) => driverId)).toEqual([
            "fast-driver",
            "slow-driver",
        ]);
        expect(
            body.map(({ routeDurationSeconds }) => routeDurationSeconds),
        ).toEqual([100, 500]);
        expect(
            body.every(({ lateBySeconds }) => Number(lateBySeconds) > 0),
        ).toBeTrue();
        expect(body[0]?.rankingTrace).toMatchObject({
            rank: 1,
            comparedTo: {
                driverId: "slow-driver",
                driverName: "Slow Driver",
                relation: "ahead",
            },
            decisiveCriterion: "estimatedPickupAt",
            steps: expect.arrayContaining([
                expect.objectContaining({
                    criterion: "estimatedPickupAt",
                    outcome: "better",
                    code: "estimatedPickupAt.better",
                    values: {
                        candidate: expect.any(String),
                        comparedTo: expect.any(String),
                        unit: "dateTime",
                    },
                }),
            ]),
        });
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

describe("assignment notifications", () => {
    const geocoderResult = (displayName: string) => ({
        count: 1,
        results: [
            {
                pack: "austria.sqlite",
                kind: "address",
                lat: 48.2,
                lon: 16.3,
                display_name: displayName,
                distance_m: 5,
            },
        ],
    });

    const [baseJob] = exampleData.job;
    if (!baseJob) throw new Error("Expected job fixture data");
    const assignedJob = {
        ...baseJob,
        assignedDriverId: "driver-2",
        from: [48.2082, 16.3738] as [number, number],
        to: [48.1947, 16.3122] as [number, number],
    };

    beforeEach(() => {
        setDbMockRows("select", [[assignedJob.assignedDriverId]]);
    });

    it("shortens addresses to the requested Unicode character length", () => {
        expect(shortenAddress("Short address", 20)).toBe("Short address");
        expect(shortenAddress("Stephansplatz 😀 Vienna", 15)).toBe(
            "Stephansplatz…",
        );
        expect(Array.from(shortenAddress("x".repeat(100), 20))).toHaveLength(
            20,
        );
    });

    it("reverse geocodes both locations and publishes the shortened notification", async () => {
        const longPickupAddress = "A".repeat(100);
        fetchMock
            .mockResolvedValueOnce(
                Response.json(geocoderResult(longPickupAddress)),
            )
            .mockResolvedValueOnce(
                Response.json(geocoderResult("Schönbrunner Straße 1, Wien")),
            );
        const publishMock = mock((_topic: string, _message: string) => 1);
        const server = {
            publish: publishMock,
        } as unknown as Bun.Server<unknown>;

        await sendAssignmentNotification(server, assignedJob);

        expect(fetchMock).toHaveBeenCalledTimes(2);
        expect(fetchMock.mock.calls.map(([input]) => String(input))).toEqual([
            `${envMock.GEOCODER_URL}/reverse?lat=48.2082&lon=16.3738&limit=1`,
            `${envMock.GEOCODER_URL}/reverse?lat=48.1947&lon=16.3122&limit=1`,
        ]);
        expect(publishMock).toHaveBeenCalledTimes(1);
        expect(publishMock.mock.calls[0]?.[0]).toBe("api:ws:notify:driver-2");
        const notification = JSON.parse(String(publishMock.mock.calls[0]?.[1]));
        expect(notification).toEqual({
            jobId: assignedJob.id,
            from: `${"A".repeat(NOTIFICATION_ADDRESS_MAX_LENGTH - 1)}…`,
            to: "Schönbrunner Straße 1, Wien",
            note: assignedJob.note,
        });
    });

    it("uses destination coordinates and still publishes when destination geocoding fails", async () => {
        fetchMock
            .mockResolvedValueOnce(
                Response.json(geocoderResult("Stephansplatz 1, Wien")),
            )
            .mockRejectedValueOnce(new Error("geocoder unavailable"));
        const publishMock = mock((_topic: string, _message: string) => 1);
        const server = {
            publish: publishMock,
        } as unknown as Bun.Server<unknown>;

        await sendAssignmentNotification(server, assignedJob);

        expect(publishMock).toHaveBeenCalledTimes(1);
        expect(JSON.parse(String(publishMock.mock.calls[0]?.[1]))).toEqual({
            jobId: assignedJob.id,
            from: "Stephansplatz 1, Wien",
            to: "48.1947, 16.3122",
            note: assignedJob.note,
        });
    });

    it("uses pickup coordinates and still publishes when pickup geocoding fails", async () => {
        fetchMock
            .mockRejectedValueOnce(new Error("geocoder unavailable"))
            .mockResolvedValueOnce(
                Response.json(geocoderResult("Schönbrunner Straße 1, Wien")),
            );
        const publishMock = mock((_topic: string, _message: string) => 1);
        const server = {
            publish: publishMock,
        } as unknown as Bun.Server<unknown>;

        await sendAssignmentNotification(server, assignedJob);

        expect(publishMock).toHaveBeenCalledTimes(1);
        expect(JSON.parse(String(publishMock.mock.calls[0]?.[1]))).toEqual({
            jobId: assignedJob.id,
            from: "48.2082, 16.3738",
            to: "Schönbrunner Straße 1, Wien",
            note: assignedJob.note,
        });
    });

    it.each([
        ["reassigned", "driver-3"],
        ["cancelled", null],
    ])("suppresses a notification when the job is %s while geocoding is pending", async (_state, currentDriverId) => {
        const pendingResponses: Array<(response: Response) => void> = [];
        fetchMock.mockImplementation(
            () =>
                new Promise<Response>((resolve) => {
                    pendingResponses.push(resolve);
                }),
        );
        const publishMock = mock((_topic: string, _message: string) => 1);
        const server = {
            publish: publishMock,
        } as unknown as Bun.Server<unknown>;

        const notification = sendAssignmentNotification(server, assignedJob);
        expect(pendingResponses).toHaveLength(2);
        expect(dbClientQueryMock).not.toHaveBeenCalled();

        setDbMockRows("select", [[currentDriverId]]);
        for (const resolve of pendingResponses) {
            resolve(Response.json(geocoderResult("Resolved address")));
        }
        await notification;

        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
        expect(getFirstQuery().values).toEqual([assignedJob.id, 1]);
        expect(publishMock).not.toHaveBeenCalled();
    });

    it("starts reverse geocoding without waiting for it to finish", () => {
        fetchMock.mockImplementation(
            () => new Promise<Response>(() => undefined),
        );
        const publishMock = mock((_topic: string, _message: string) => 1);
        const server = {
            publish: publishMock,
        } as unknown as Bun.Server<unknown>;

        const result = notifyAssignedDriverInBackground(server, assignedJob);

        expect(result).toBeUndefined();
        expect(fetchMock).toHaveBeenCalledTimes(2);
        expect(publishMock).not.toHaveBeenCalled();
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

    it("allows the assigned driver to cancel the job", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await mutationRequest("cancel");

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            message: "Job canceled successfully",
        });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(2);
        const { sql, values } = getFirstQuery();
        expect(sql).toContain('from "job"');
        expect(sql).toContain('"job"."id" = $1');
        expect(sql).toContain('"job"."assigned_driver_id" = $2');
        expect(values).toEqual([jobId, session.user.id, 1]);
    });

    it("returns 403 when the driver is not assigned to the job", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockTableRows("job", []);

        const response = await mutationRequest("cancel");

        expect(response.status).toBe(403);
        expect(await response.json()).toEqual({
            error: "You are not authorized to cancel this job",
        });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
        const { sql, values } = getFirstQuery();
        expect(sql).toContain('"job"."id" = $1');
        expect(sql).toContain('"job"."assigned_driver_id" = $2');
        expect(values).toEqual([jobId, session.user.id, 1]);
    });

    it("allows an admin to cancel without checking the assignment", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await mutationRequest("cancel");

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            message: "Job canceled successfully",
        });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
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
        getSessionMock.mockResolvedValue(adminSession);
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
        getSessionMock.mockResolvedValue(adminSession);
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

describe("DELETE /jobs/:id", () => {
    it("returns 401 without a session and does not delete the job", async () => {
        const response = await deleteJobRequest(false);

        expect(response.status).toBe(401);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("deletes an unassigned job", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        setDbMockRowCount("delete", 1);

        const response = await deleteJobRequest();

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            message: "Job deleted successfully",
        });

        const { sql, values } = getFirstQuery();
        expect(sql).toContain('delete from "job"');
        expect(sql).toContain('"job"."assigned_driver_id" is null');
        expect(values).toEqual([jobId]);
    });

    it("returns 404 when no unassigned job matches", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await deleteJobRequest();

        expect(response.status).toBe(404);
        expect(await response.json()).toEqual({
            error: "Unassigned job not found",
        });
    });
});

describe("GET /jobs/:id", () => {
    it("returns 401 without a session and does not query the database", async () => {
        const response = await getJobRequest(false);

        expect(response.status).toBe(401);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("returns the requested job without geocoding by default", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await getJobRequest();

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual(serializedJob);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
        expect(fetchMock).not.toHaveBeenCalled();
        const { sql, values } = getFirstQuery();
        expect(sql).toContain('from "job" where "job"."id" = $1 limit $2');
        expect(values).toEqual([jobId, 1]);
    });

    it("enriches the requested job when ?geocode is present", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await getJobRequest(true, jobId, true);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual(serializedJobWithAddresses);
        expect(fetchMock).toHaveBeenCalledTimes(2);
    });

    it("returns a null destination address when the job has no destination", async () => {
        getSessionMock.mockResolvedValue(session);
        const [jobRow] = getDbMockTableRows("job");
        if (!jobRow) throw new Error("Expected job fixture data");
        jobRow[4] = null;
        setDbMockRows("select", [jobRow]);

        const response = await getJobRequest(true, jobId, true);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            ...serializedJobWithAddresses,
            to: null,
            toAddress: null,
        });
        expect(fetchMock).toHaveBeenCalledTimes(1);
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

describe("GET /jobs/all", () => {
    it("returns 401 without a session and does not query the database", async () => {
        const response = await allJobsRequest(undefined, false);

        expect(response.status).toBe(401);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it.each([
        ["an omitted filter", undefined],
        ['filter="all"', "all"],
    ])("returns every job newest first for %s", async (_description, filter) => {
        getSessionMock.mockResolvedValue(adminSession);
        setDbMockRows(
            "select",
            getDbMockTableRows("job").map((row) => [
                ...row,
                exampleData.user[0]?.name,
            ]),
        );

        const response = await allJobsRequest(filter);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([
            {
                ...serializedJob,
                assignedDriverName: exampleData.user[0]?.name,
            },
        ]);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
        expect(fetchMock).not.toHaveBeenCalled();

        const { sql, values } = getFirstQuery();
        expect(sql).toContain('from "job" left join "user"');
        expect(sql).toContain('order by "job"."created_at" desc');
        expect(sql).not.toContain(" where ");
        expect(values).toEqual([]);
    });

    it("returns only assigned jobs when requested", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await allJobsRequest("assigned");

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([serializedJob]);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);

        const { sql, values } = getFirstQuery();
        expect(sql).toContain('left join "user"');
        expect(sql).toContain('"job"."assigned_driver_id" is not null');
        expect(sql).toContain('order by "job"."created_at" desc');
        expect(values).toEqual([]);
    });

    it("enriches all jobs when ?geocode is present", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await allJobsRequest(undefined, true, true);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([serializedJobWithAddresses]);
        expect(fetchMock).toHaveBeenCalledTimes(2);
    });

    it("returns only unassigned jobs when requested", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        const [unassignedJobRow] = getDbMockTableRows("job");
        if (!unassignedJobRow) throw new Error("Expected job fixture data");
        unassignedJobRow[1] = null;
        setDbMockRows("select", [unassignedJobRow]);

        const response = await allJobsRequest("unassigned");

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([
            { ...serializedJob, assignedDriverId: null },
        ]);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);

        const { sql, values } = getFirstQuery();
        expect(sql).toContain('left join "user"');
        expect(sql).toContain('"job"."assigned_driver_id" is null');
        expect(sql).toContain('order by "job"."created_at" desc');
        expect(values).toEqual([]);
    });

    it("returns an empty list when no jobs match", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        setDbMockRows("select", []);

        const response = await allJobsRequest("assigned");

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([]);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });

    it("returns 422 for an unsupported filter and does not query the database", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await allJobsRequest("completed");

        expect(response.status).toBe(422);
        expect(getSessionMock).not.toHaveBeenCalled();
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("returns 500 when the job lookup fails", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        dbClientQueryMock.mockRejectedValueOnce(
            new Error("database unavailable"),
        );

        const response = await allJobsRequest();

        expect(response.status).toBe(500);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });
});
