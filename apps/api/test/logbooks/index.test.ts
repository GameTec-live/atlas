import { beforeEach, describe, expect, it } from "bun:test";
import { Elysia } from "elysia";
import { getSessionMock, resetAuthMocks, session } from "../mocks/auth";
import {
    dbClientQueryMock,
    exampleData,
    getDbMockTableRows,
    resetDbMocks,
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
