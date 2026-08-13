import {
    afterEach,
    beforeEach,
    describe,
    expect,
    it,
    setSystemTime,
} from "bun:test";
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

const { logbooks } = await import("@/src/logbooks");
const app = new Elysia().use(logbooks);

const first = <T>(items: readonly T[], label: string): T => {
    const item = items[0];
    if (item === undefined) {
        throw new Error(`Expected ${label} fixture data`);
    }
    return item;
};

const exampleVehicle = first(exampleData.vehicle, "vehicle");
const exampleLogbook = first(exampleData.logbook, "logbook");
const vehicleId = exampleVehicle.id;
const fingerprint = "device-123";
const submitBody = {
    vehicleId,
    startedAt: exampleLogbook.startedAt.toISOString(),
    startOdometer: exampleLogbook.startOdometer,
    endOdometer: exampleLogbook.endOdometer,
    endedAt: exampleLogbook.endedAt?.toISOString(),
    revenue: exampleLogbook.revenue,
};

const serializedLogbook = {
    ...exampleLogbook,
    startedAt: exampleLogbook.startedAt.toISOString(),
    endedAt: exampleLogbook.endedAt?.toISOString(),
    createdAt: exampleLogbook.createdAt.toISOString(),
    updatedAt: exampleLogbook.updatedAt.toISOString(),
};

const adminSession = {
    ...session,
    user: {
        ...session.user,
        role: "admin",
    },
};

const serializedFetchedLogbook = {
    id: serializedLogbook.id,
    vehicleId: serializedLogbook.vehicleId,
    driverId: serializedLogbook.driverId,
    driverName: first(exampleData.user, "user").name,
    startOdometer: serializedLogbook.startOdometer,
    endOdometer: serializedLogbook.endOdometer,
    startedAt: serializedLogbook.startedAt,
    endedAt: serializedLogbook.endedAt,
    revenue: serializedLogbook.revenue,
    createdAt: serializedLogbook.createdAt,
    updatedAt: serializedLogbook.updatedAt,
    vehicle: {
        id: exampleVehicle.id,
        licensePlate: exampleVehicle.licensePlate,
        brand: exampleVehicle.brand,
        model: exampleVehicle.model,
        year: exampleVehicle.year,
    },
};

const submitRequest = (body: unknown, authenticated = true) => {
    const headers = new Headers({ "content-type": "application/json" });
    if (authenticated) {
        headers.set("authorization", "Bearer test-token");
    }

    return app.handle(
        new Request("http://localhost/logbooks/submit", {
            method: "POST",
            headers,
            body: JSON.stringify(body),
        }),
    );
};

const fetchRequest = (path: string) =>
    app.handle(
        new Request(`http://localhost/logbooks${path}`, {
            headers: { authorization: "Bearer test-token" },
        }),
    );

const deleteRequest = (id = exampleLogbook.id) =>
    app.handle(
        new Request(`http://localhost/logbooks/${id}`, {
            method: "DELETE",
            headers: { authorization: "Bearer test-token" },
        }),
    );

const useJoinedLogbookRow = () => {
    const logbookRow = first(getDbMockTableRows("logbook"), "logbook row");
    const vehicleRow = first(getDbMockTableRows("vehicle"), "vehicle row");
    const driver = first(exampleData.user, "user");

    setDbMockRows("select", [
        [
            logbookRow[0],
            logbookRow[1],
            logbookRow[2],
            driver.name,
            ...logbookRow.slice(3, 8),
            ...logbookRow.slice(9),
            vehicleRow[0],
            vehicleRow[5],
            vehicleRow[2],
            vehicleRow[3],
            vehicleRow[4],
        ],
    ]);
};

const getQuery = (index: number) => {
    const call = dbClientQueryMock.mock.calls[index];
    if (!call) {
        throw new Error(`Expected database call ${index + 1}`);
    }

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

afterEach(() => {
    setSystemTime();
});

describe("POST /logbooks/submit authentication", () => {
    it("returns 401 without a session and does not query the database", async () => {
        const response = await submitRequest(submitBody, false);

        expect(response.status).toBe(401);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });
});

describe("POST /logbooks/submit with a vehicle id", () => {
    it("creates a logbook entry for the authenticated driver", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockRows("insert", [
            first(getDbMockTableRows("logbook"), "logbook row"),
        ]);

        const response = await submitRequest(submitBody);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual(serializedLogbook);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);

        const { sql, values } = getQuery(0);
        expect(sql).toContain('insert into "logbook"');
        expect(sql).toContain("returning");
        expect(values).toEqual([
            vehicleId,
            session.user.id,
            submitBody.startOdometer,
            submitBody.endOdometer,
            submitBody.startedAt,
            submitBody.endedAt,
            submitBody.revenue,
        ]);
    });

    it("prefers vehicleId when both vehicle identifiers are supplied", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockRows("insert", [
            first(getDbMockTableRows("logbook"), "logbook row"),
        ]);

        const response = await submitRequest({
            ...submitBody,
            vehicleFingerprint: fingerprint,
        });

        expect(response.status).toBe(200);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
        const { sql, values } = getQuery(0);
        expect(sql).toContain('insert into "logbook"');
        expect(values[0]).toBe(vehicleId);
        expect(values).not.toContain(fingerprint);
    });
});

describe("POST /logbooks/submit with a vehicle fingerprint", () => {
    it("resolves the fingerprint before creating the entry", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockRows("select", [[vehicleId]]);
        setDbMockRows("insert", [
            first(getDbMockTableRows("logbook"), "logbook row"),
        ]);
        const { vehicleId: _vehicleId, ...bodyWithoutVehicleId } = submitBody;

        const response = await submitRequest({
            ...bodyWithoutVehicleId,
            vehicleFingerprint: fingerprint,
        });

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual(serializedLogbook);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(2);

        const lookup = getQuery(0);
        expect(lookup.sql).toContain(
            'select "id" from "vehicle" where "vehicle"."fingerprint" = $1',
        );
        expect(lookup.values).toEqual([fingerprint]);

        const insert = getQuery(1);
        expect(insert.sql).toContain('insert into "logbook"');
        expect(insert.values[0]).toBe(vehicleId);
        expect(insert.values[1]).toBe(session.user.id);
    });

    it("returns 404 without inserting when the fingerprint is unknown", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockRows("select", []);
        const { vehicleId: _vehicleId, ...bodyWithoutVehicleId } = submitBody;

        const response = await submitRequest({
            ...bodyWithoutVehicleId,
            vehicleFingerprint: "unknown-device",
        });

        expect(response.status).toBe(404);
        expect(await response.text()).toBe("Vehicle not found");
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
        expect(getQuery(0).sql).toContain('select "id" from "vehicle"');
    });

    it("returns 500 when fingerprint resolution fails", async () => {
        getSessionMock.mockResolvedValue(session);
        dbClientQueryMock.mockRejectedValueOnce(
            new Error("database unavailable"),
        );
        const { vehicleId: _vehicleId, ...bodyWithoutVehicleId } = submitBody;

        const response = await submitRequest({
            ...bodyWithoutVehicleId,
            vehicleFingerprint: fingerprint,
        });

        expect(response.status).toBe(500);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });
});

describe("POST /logbooks/submit errors and validation", () => {
    it("returns 400 when neither vehicle identifier is supplied", async () => {
        getSessionMock.mockResolvedValue(session);
        const { vehicleId: _vehicleId, ...bodyWithoutVehicleId } = submitBody;

        const response = await submitRequest(bodyWithoutVehicleId);

        expect(response.status).toBe(400);
        expect(await response.text()).toBe(
            "Either vehicleId or vehicleFingerprint must be provided",
        );
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("returns 500 when the inserted entry is not returned", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockRows("insert", []);

        const response = await submitRequest(submitBody);

        expect(response.status).toBe(500);
        expect(await response.text()).toBe("Failed to create logbook entry");
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });

    it("returns 500 when the insert fails", async () => {
        getSessionMock.mockResolvedValue(session);
        dbClientQueryMock.mockRejectedValueOnce(
            new Error("database unavailable"),
        );

        const response = await submitRequest(submitBody);

        expect(response.status).toBe(500);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });

    it.each([
        ["a non-UUID vehicle id", { ...submitBody, vehicleId: "not-a-uuid" }],
        ["a missing start time", { ...submitBody, startedAt: undefined }],
        ["an invalid start time", { ...submitBody, startedAt: "not-a-date" }],
        ["a fractional start odometer", { ...submitBody, startOdometer: 1.5 }],
        ["a missing end odometer", { ...submitBody, endOdometer: undefined }],
        ["an invalid end time", { ...submitBody, endedAt: "not-a-date" }],
        ["a non-numeric revenue", { ...submitBody, revenue: "84.50" }],
    ])("returns 422 for %s without querying the database", async (_, body) => {
        getSessionMock.mockResolvedValue(session);

        const response = await submitRequest(body);

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });
});

describe("logbook fetch authentication", () => {
    const fetchPaths = [
        "/",
        `/${exampleLogbook.id}`,
        `/vehicle/${vehicleId}`,
        `/driver/${exampleLogbook.driverId}`,
        "/date",
    ];

    it.each(
        fetchPaths,
    )("returns 401 for an unauthenticated GET %s without querying the database", async (path) => {
        const response = await fetchRequest(path);

        expect(response.status).toBe(401);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it.each(
        fetchPaths,
    )("returns 403 for a non-admin GET %s without querying the database", async (path) => {
        getSessionMock.mockResolvedValue(session);

        const response = await fetchRequest(path);

        expect(response.status).toBe(403);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });
});

describe("GET /logbooks/", () => {
    it("returns every logbook with its driver name and vehicle summary", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        useJoinedLogbookRow();

        const response = await fetchRequest("/");

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([serializedFetchedLogbook]);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);

        const { sql, values } = getQuery(0);
        expect(sql).toContain('from "logbook"');
        expect(sql).toContain(
            'left join "vehicle" on "logbook"."vehicle_id" = "vehicle"."id"',
        );
        expect(sql).toContain(
            'left join "user" on "logbook"."driver_id" = "user"."id"',
        );
        expect(sql).not.toContain(" where ");
        expect(values).toEqual([]);
    });

    it("returns an empty list when no logbook entries exist", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        setDbMockRows("select", []);

        const response = await fetchRequest("/");

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([]);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });
});

describe("GET /logbooks/:id", () => {
    it("filters by logbook id and returns the joined entry", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        useJoinedLogbookRow();

        const response = await fetchRequest(`/${exampleLogbook.id}`);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual(serializedFetchedLogbook);

        const { sql, values } = getQuery(0);
        expect(sql).toContain('where "logbook"."id" = $1');
        expect(values).toEqual([exampleLogbook.id]);
    });

    it("returns 404 when the logbook id does not exist", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        setDbMockRows("select", []);

        const response = await fetchRequest(`/${exampleLogbook.id}`);

        expect(response.status).toBe(404);
        expect(await response.json()).toEqual({
            error: "Logbook entry not found",
        });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });

    it("returns 422 for a non-UUID id without querying the database", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await fetchRequest("/not-a-uuid");

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });
});

describe("DELETE /logbooks/:id", () => {
    it("returns 401 without a session and does not update the entry", async () => {
        const response = await deleteRequest();

        expect(response.status).toBe(401);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("returns 403 for a non-admin and does not update the entry", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await deleteRequest();

        expect(response.status).toBe(403);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("marks the requested entry invalid", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        setDbMockRowCount("update", 1);

        const response = await deleteRequest();

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            message: "Logbook entry marked invalid",
        });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);

        const { sql, values } = getQuery(0);
        expect(sql).toContain(
            'update "logbook" set "invalid" = $1, "updated_at" = $2 where "logbook"."id" = $3',
        );
        expect(values).toHaveLength(3);
        expect(values[0]).toBe(true);
        expect(values[1]).toEqual(expect.any(String));
        expect(
            Number.isNaN(new Date(values[1] as string).getTime()),
        ).toBeFalse();
        expect(values[2]).toBe(exampleLogbook.id);
    });

    it("returns 404 when the logbook entry does not exist", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        setDbMockRowCount("update", 0);

        const response = await deleteRequest();

        expect(response.status).toBe(404);
        expect(await response.json()).toEqual({
            error: "Logbook entry not found",
        });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });

    it("returns 422 for a non-UUID id without querying the database", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await deleteRequest("not-a-uuid");

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("returns 500 when the update fails", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        dbClientQueryMock.mockRejectedValueOnce(
            new Error("database unavailable"),
        );

        const response = await deleteRequest();

        expect(response.status).toBe(500);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });
});

describe("GET /logbooks/vehicle/:vehicleId", () => {
    it("filters entries by vehicle id", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        useJoinedLogbookRow();

        const response = await fetchRequest(`/vehicle/${vehicleId}`);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([serializedFetchedLogbook]);

        const { sql, values } = getQuery(0);
        expect(sql).toContain('where "logbook"."vehicle_id" = $1');
        expect(values).toEqual([vehicleId]);
    });

    it("returns an empty list when the vehicle has no entries", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        setDbMockRows("select", []);

        const response = await fetchRequest(`/vehicle/${vehicleId}`);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([]);
    });

    it("returns 422 for a non-UUID vehicle id without querying the database", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await fetchRequest("/vehicle/not-a-uuid");

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });
});

describe("GET /logbooks/driver/:driverId", () => {
    it("filters entries by driver id", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        useJoinedLogbookRow();

        const response = await fetchRequest(
            `/driver/${exampleLogbook.driverId}`,
        );

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([serializedFetchedLogbook]);

        const { sql, values } = getQuery(0);
        expect(sql).toContain('where "logbook"."driver_id" = $1');
        expect(values).toEqual([exampleLogbook.driverId]);
    });

    it("returns an empty list when the driver has no entries", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        setDbMockRows("select", []);

        const response = await fetchRequest("/driver/unknown-driver");

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([]);

        const { values } = getQuery(0);
        expect(values).toEqual(["unknown-driver"]);
    });
});

describe("GET /logbooks/date", () => {
    const startDate = "2026-07-20T08:00:00.000Z";
    const endDate = "2026-07-20T18:00:00.000Z";
    const exactDate = "2026-07-20T09:15:00.000Z";

    const dateRequest = (query: Record<string, string> = {}) => {
        const search = new URLSearchParams(query).toString();
        return fetchRequest(`/date${search ? `?${search}` : ""}`);
    };

    it("uses exactDate in preference to the range parameters", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        useJoinedLogbookRow();

        const response = await dateRequest({
            exactDate,
            startDate,
            endDate,
        });

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([serializedFetchedLogbook]);

        const { sql, values } = getQuery(0);
        expect(sql).toContain('where "logbook"."started_at" = $1');
        expect(sql).not.toContain(" between ");
        expect(values).toEqual([exactDate]);
    });

    it("filters inclusively between startDate and endDate", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        useJoinedLogbookRow();

        const response = await dateRequest({ startDate, endDate });

        expect(response.status).toBe(200);

        const { sql, values } = getQuery(0);
        expect(sql).toContain('where "logbook"."started_at" between $1 and $2');
        expect(values).toEqual([startDate, endDate]);
    });

    it("filters from startDate when no endDate is supplied", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        useJoinedLogbookRow();

        const response = await dateRequest({ startDate });

        expect(response.status).toBe(200);

        const { sql, values } = getQuery(0);
        expect(sql).toContain('where "logbook"."started_at" >= $1');
        expect(values).toEqual([startDate]);
    });

    it("filters through endDate when no startDate is supplied", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        useJoinedLogbookRow();

        const response = await dateRequest({ endDate });

        expect(response.status).toBe(200);

        const { sql, values } = getQuery(0);
        expect(sql).toContain('where "logbook"."started_at" <= $1');
        expect(values).toEqual([endDate]);
    });

    it("defaults to entries started since local midnight", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        useJoinedLogbookRow();
        setSystemTime(new Date("2026-07-20T14:32:10.000Z"));
        const expectedMidnight = new Date();
        expectedMidnight.setHours(0, 0, 0, 0);

        const response = await dateRequest();

        expect(response.status).toBe(200);

        const { sql, values } = getQuery(0);
        expect(sql).toContain('where "logbook"."started_at" >= $1');
        expect(values).toEqual([expectedMidnight.toISOString()]);
    });

    it.each([
        "startDate",
        "endDate",
        "exactDate",
    ])("returns 422 for an invalid %s without querying the database", async (parameter) => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await dateRequest({ [parameter]: "not-a-date" });

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });
});

describe("logbook fetch database errors", () => {
    it.each([
        "/",
        `/${exampleLogbook.id}`,
        `/vehicle/${vehicleId}`,
        `/driver/${exampleLogbook.driverId}`,
        "/date",
    ])("returns 500 when GET %s cannot query the database", async (path) => {
        getSessionMock.mockResolvedValue(adminSession);
        dbClientQueryMock.mockRejectedValueOnce(
            new Error("database unavailable"),
        );

        const response = await fetchRequest(path);

        expect(response.status).toBe(500);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });
});
