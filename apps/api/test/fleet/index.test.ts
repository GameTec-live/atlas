import { beforeEach, describe, expect, it } from "bun:test";
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

const { fleet } = await import("@/src/fleet");
const app = new Elysia().use(fleet);

const adminSession = {
    ...session,
    user: {
        ...session.user,
        role: "admin",
    },
};

const first = <T>(items: readonly T[], label: string): T => {
    const item = items[0];
    if (item === undefined) {
        throw new Error(`Expected ${label} fixture data`);
    }
    return item;
};

const exampleVehicle = first(exampleData.vehicle, "vehicle");
const exampleMaintenance = first(exampleData.maintenance, "maintenance");
const vehicleId = exampleVehicle.id;
const vehicleBody = {
    brand: "Volkswagen",
    model: "Crafter",
    year: 2025,
    licensePlate: "ATLAS-2",
    odometer: 0,
    fuelLevel: 100,
    maintenanceEvery: 0,
    assessmentMonth: 8,
    smartSupport: false,
};

const serializedVehicle = {
    ...exampleVehicle,
    createdAt: exampleVehicle.createdAt.toISOString(),
    updatedAt: exampleVehicle.updatedAt.toISOString(),
};

const serializedMaintenance = {
    ...exampleMaintenance,
    createdAt: exampleMaintenance.createdAt.toISOString(),
    updatedAt: exampleMaintenance.updatedAt.toISOString(),
};

const request = (path: string, init: RequestInit = {}) => {
    const headers = new Headers(init.headers);
    headers.set("authorization", "Bearer test-token");

    return app.handle(
        new Request(`http://localhost/fleet${path}`, {
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

const useJoinedVehicleRow = () => {
    const vehicleRow = first(getDbMockTableRows("vehicle"), "vehicle row");
    const maintenanceRow = first(
        getDbMockTableRows("maintenance"),
        "maintenance row",
    );
    setDbMockRows("select", [[...vehicleRow, ...maintenanceRow]]);
};

const getFirstQuery = () => {
    const call = dbClientQueryMock.mock.calls[0];
    if (!call) {
        throw new Error("Expected a database call");
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

describe("fleet authentication", () => {
    it.each([
        ["GET", "/vehicles", undefined],
        ["GET", `/vehicles/${vehicleId}`, undefined],
        ["GET", `/vehicles/${vehicleId}/maintenance`, undefined],
        ["POST", "/vehicles", vehicleBody],
        ["PUT", `/vehicles/${vehicleId}`, { model: "Crafter" }],
        ["DELETE", `/vehicles/${vehicleId}`, undefined],
        ["POST", `/vehicles/${vehicleId}/maintenance`, { odometer: 13_000 }],
        ["GET", "/fingerprint/device-123", undefined],
        ["GET", "/fingerprint/candidates", undefined],
        ["POST", "/fingerprint/pair", { vehicleId, fingerprint: "device-123" }],
    ])("returns 401 for an unauthenticated %s %s", async (method, path, body) => {
        const response = await app.handle(
            new Request(`http://localhost/fleet${path}`, {
                method,
                headers:
                    body === undefined
                        ? undefined
                        : { "content-type": "application/json" },
                body: body === undefined ? undefined : JSON.stringify(body),
            }),
        );

        expect(response.status).toBe(401);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it.each([
        ["POST", "/vehicles", vehicleBody],
        ["PUT", `/vehicles/${vehicleId}`, { model: "Crafter" }],
        ["DELETE", `/vehicles/${vehicleId}`, undefined],
        ["POST", `/vehicles/${vehicleId}/maintenance`, { odometer: 13_000 }],
    ])("returns 403 for an authenticated non-admin %s %s", async (method, path, body) => {
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
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });
});

describe("GET /fleet/vehicles", () => {
    it("returns every vehicle with its latest maintenance record", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        useJoinedVehicleRow();

        const response = await request("/vehicles");

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([
            {
                vehicle: serializedVehicle,
                maintenance: serializedMaintenance,
            },
        ]);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);

        const { sql, values } = getFirstQuery();
        expect(sql).toContain('select distinct on ("vehicle"."id")');
        expect(sql).toContain(
            'left join "maintenance" on "vehicle"."id" = "maintenance"."vehicle_id"',
        );
        expect(sql).toContain(
            'order by "vehicle"."id", "maintenance"."created_at" desc',
        );
        expect(values).toEqual([]);
    });

    it("returns an empty list when there are no vehicles", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        setDbMockRows("select", []);

        const response = await request("/vehicles");

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([]);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });
});

describe("GET /fleet/vehicles/:id", () => {
    it("returns the vehicle and its latest maintenance record", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        useJoinedVehicleRow();

        const response = await request(`/vehicles/${vehicleId}`);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            vehicle: serializedVehicle,
            maintenance: serializedMaintenance,
        });

        const { sql, values } = getFirstQuery();
        expect(sql).toContain(
            'where "vehicle"."id" = $1 order by "maintenance"."created_at" desc limit $2',
        );
        expect(values).toEqual([vehicleId, 1]);
    });

    it("returns null maintenance when the vehicle has no maintenance records", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        const vehicleRow = first(getDbMockTableRows("vehicle"), "vehicle row");
        setDbMockRows("select", [[...vehicleRow, ...Array(7).fill(null)]]);

        const response = await request(`/vehicles/${vehicleId}`);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            vehicle: serializedVehicle,
            maintenance: null,
        });
    });

    it("returns 404 when the vehicle does not exist", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        setDbMockRows("select", []);

        const response = await request(`/vehicles/${vehicleId}`);

        expect(response.status).toBe(404);
        expect(await response.json()).toEqual({ error: "Vehicle not found" });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });

    it("returns 422 for a non-UUID id without querying the database", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await request("/vehicles/not-a-uuid");

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });
});

describe("POST /fleet/vehicles", () => {
    it("creates a vehicle using every supported field", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        const createdVehicleRow = first(
            getDbMockTableRows("vehicle"),
            "vehicle row",
        );
        createdVehicleRow[2] = vehicleBody.brand;
        createdVehicleRow[3] = vehicleBody.model;
        createdVehicleRow[4] = vehicleBody.year;
        createdVehicleRow[5] = vehicleBody.licensePlate;
        createdVehicleRow[6] = vehicleBody.odometer;
        createdVehicleRow[7] = vehicleBody.fuelLevel;
        createdVehicleRow[8] = vehicleBody.maintenanceEvery;
        createdVehicleRow[9] = vehicleBody.assessmentMonth;
        createdVehicleRow[10] = vehicleBody.smartSupport;
        setDbMockRows("insert", [createdVehicleRow]);

        const response = await jsonRequest("/vehicles", "POST", vehicleBody);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            ...serializedVehicle,
            ...vehicleBody,
        });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);

        const { sql, values } = getFirstQuery();
        expect(sql).toContain('insert into "vehicle"');
        expect(sql).toContain("returning");
        expect(values).toEqual([
            vehicleBody.brand,
            vehicleBody.model,
            vehicleBody.year,
            vehicleBody.licensePlate,
            vehicleBody.odometer,
            vehicleBody.fuelLevel,
            vehicleBody.maintenanceEvery,
            vehicleBody.assessmentMonth,
            vehicleBody.smartSupport,
        ]);
    });

    it("creates a vehicle when optional fields are omitted", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        const { odometer, fuelLevel, smartSupport, ...requiredBody } =
            vehicleBody;
        const createdVehicleRow = first(
            getDbMockTableRows("vehicle"),
            "vehicle row",
        );
        createdVehicleRow[2] = requiredBody.brand;
        createdVehicleRow[3] = requiredBody.model;
        createdVehicleRow[4] = requiredBody.year;
        createdVehicleRow[5] = requiredBody.licensePlate;
        createdVehicleRow[6] = null;
        createdVehicleRow[7] = null;
        createdVehicleRow[8] = requiredBody.maintenanceEvery;
        createdVehicleRow[9] = requiredBody.assessmentMonth;
        createdVehicleRow[10] = true;
        setDbMockRows("insert", [createdVehicleRow]);

        const response = await jsonRequest("/vehicles", "POST", requiredBody);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            ...serializedVehicle,
            ...requiredBody,
            odometer: null,
            fuelLevel: null,
            smartSupport: true,
        });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });

    it("returns 500 when the inserted vehicle is not returned", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        setDbMockRows("insert", []);

        const response = await jsonRequest("/vehicles", "POST", vehicleBody);

        expect(response.status).toBe(500);
        expect(await response.json()).toEqual({
            error: "Failed to create vehicle",
        });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });

    it.each([
        ["a missing brand", { ...vehicleBody, brand: undefined }],
        ["an invalid year", { ...vehicleBody, year: "not-a-year" }],
        ["a negative odometer", { ...vehicleBody, odometer: -1 }],
        ["a fuel level below zero", { ...vehicleBody, fuelLevel: -0.01 }],
        ["a fuel level above 100", { ...vehicleBody, fuelLevel: 100.01 }],
        [
            "a negative maintenance interval",
            { ...vehicleBody, maintenanceEvery: -1 },
        ],
        [
            "an invalid assessment month",
            { ...vehicleBody, assessmentMonth: 13 },
        ],
    ])("returns 422 for %s", async (_description, body) => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await jsonRequest("/vehicles", "POST", body);

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });
});

describe("PUT /fleet/vehicles/:id", () => {
    it("updates the supplied vehicle fields and automatic timestamp", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        setDbMockRowCount("update", 1);

        const response = await jsonRequest(`/vehicles/${vehicleId}`, "PUT", {
            model: "Crafter",
        });

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            message: "Vehicle updated successfully",
        });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);

        const { sql, values } = getFirstQuery();
        expect(sql).toContain(
            'update "vehicle" set "model" = $1, "updated_at" = $2 where "vehicle"."id" = $3',
        );
        expect(values).toHaveLength(3);
        expect(values[0]).toBe("Crafter");
        expect(values[1]).toEqual(expect.any(String));
        expect(
            Number.isNaN(new Date(values[1] as string).getTime()),
        ).toBeFalse();
        expect(values[2]).toBe(vehicleId);
    });

    it("returns 404 when the vehicle does not exist", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await jsonRequest(`/vehicles/${vehicleId}`, "PUT", {
            fuelLevel: 50,
        });

        expect(response.status).toBe(404);
        expect(await response.json()).toEqual({ error: "Vehicle not found" });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });

    it.each([
        ["a non-UUID id", "/vehicles/not-a-uuid", { model: "Crafter" }],
        [
            "an out-of-range fuel level",
            `/vehicles/${vehicleId}`,
            { fuelLevel: 101 },
        ],
        [
            "a negative maintenance interval",
            `/vehicles/${vehicleId}`,
            { maintenanceEvery: -1 },
        ],
        [
            "an invalid assessment month",
            `/vehicles/${vehicleId}`,
            { assessmentMonth: 0 },
        ],
    ])("returns 422 for %s", async (_description, path, body) => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await jsonRequest(path, "PUT", body);

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });
});

describe("DELETE /fleet/vehicles/:id", () => {
    it("deletes an existing vehicle", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        setDbMockRowCount("delete", 1);

        const response = await request(`/vehicles/${vehicleId}`, {
            method: "DELETE",
        });

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            message: "Vehicle deleted successfully",
        });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);

        const { sql, values } = getFirstQuery();
        expect(sql).toContain(
            'delete from "vehicle" where "vehicle"."id" = $1',
        );
        expect(values).toEqual([vehicleId]);
    });

    it("returns 404 when the vehicle does not exist", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await request(`/vehicles/${vehicleId}`, {
            method: "DELETE",
        });

        expect(response.status).toBe(404);
        expect(await response.json()).toEqual({ error: "Vehicle not found" });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });

    it("returns 422 for a non-UUID id without querying the database", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await request("/vehicles/not-a-uuid", {
            method: "DELETE",
        });

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });
});

describe("GET /fleet/vehicles/:id/maintenance", () => {
    it("returns the complete maintenance history newest first", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        const maintenanceRow = first(
            getDbMockTableRows("maintenance"),
            "maintenance row",
        );
        setDbMockRows("select", [maintenanceRow]);

        const response = await request(`/vehicles/${vehicleId}/maintenance`);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([serializedMaintenance]);

        const { sql, values } = getFirstQuery();
        expect(sql).toContain(
            'where "maintenance"."vehicle_id" = $1 order by "maintenance"."created_at" desc',
        );
        expect(values).toEqual([vehicleId]);
    });

    it("returns an empty history when the vehicle has no records", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        setDbMockRows("select", []);

        const response = await request(`/vehicles/${vehicleId}/maintenance`);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([]);
    });

    it("returns 422 for a non-UUID id without querying the database", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await request("/vehicles/not-a-uuid/maintenance");

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });
});

describe("POST /fleet/vehicles/:id/maintenance", () => {
    it("creates a maintenance record for the vehicle", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        const maintenanceRow = first(
            getDbMockTableRows("maintenance"),
            "maintenance row",
        );
        maintenanceRow[2] = "Annual service";
        maintenanceRow[3] = 13_000;
        maintenanceRow[4] = "Atlas Workshop";
        setDbMockRows("insert", [maintenanceRow]);

        const response = await jsonRequest(
            `/vehicles/${vehicleId}/maintenance`,
            "POST",
            {
                note: "Annual service",
                odometer: 13_000,
                mechanic: "Atlas Workshop",
            },
        );

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            ...serializedMaintenance,
            note: "Annual service",
            odometer: 13_000,
            mechanic: "Atlas Workshop",
        });

        const { sql, values } = getFirstQuery();
        expect(sql).toContain('insert into "maintenance"');
        expect(sql).toContain("returning");
        expect(values).toEqual([
            vehicleId,
            "Annual service",
            13_000,
            "Atlas Workshop",
        ]);
    });

    it.each([
        ["a non-UUID vehicle id", "/vehicles/not-a-uuid/maintenance", {}],
        [
            "a negative odometer",
            `/vehicles/${vehicleId}/maintenance`,
            { odometer: -1 },
        ],
    ])("returns 422 for %s", async (_description, path, body) => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await jsonRequest(path, "POST", body);

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("returns 500 when the maintenance record is not returned", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        setDbMockRows("insert", []);

        const response = await jsonRequest(
            `/vehicles/${vehicleId}/maintenance`,
            "POST",
            {},
        );

        expect(response.status).toBe(500);
        expect(await response.json()).toEqual({
            error: "Failed to create maintenance record",
        });
    });
});

describe("GET /fleet/fingerprint/:fingerprint", () => {
    it("returns the vehicle paired with the fingerprint", async () => {
        getSessionMock.mockResolvedValue(session);
        const vehicleRow = first(getDbMockTableRows("vehicle"), "vehicle row");
        vehicleRow[1] = "device-123";
        setDbMockRows("select", [vehicleRow]);

        const response = await request("/fingerprint/device-123");

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            ...serializedVehicle,
            fingerprint: "device-123",
        });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);

        const { sql, values } = getFirstQuery();
        expect(sql).toContain(
            'from "vehicle" where "vehicle"."fingerprint" = $1',
        );
        expect(values).toEqual(["device-123"]);
    });

    it("returns 404 when no vehicle has the fingerprint", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockRows("select", []);

        const response = await request("/fingerprint/unknown-device");

        expect(response.status).toBe(404);
        expect(await response.json()).toEqual({ error: "Vehicle not found" });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });

    it("returns 500 when the lookup fails", async () => {
        getSessionMock.mockResolvedValue(session);
        dbClientQueryMock.mockRejectedValueOnce(
            new Error("database unavailable"),
        );

        const response = await request("/fingerprint/device-123");

        expect(response.status).toBe(500);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });
});

describe("GET /fleet/fingerprint/candidates", () => {
    it("returns only the pairing fields for unpaired vehicles", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockRows("select", [
            [
                exampleVehicle.id,
                exampleVehicle.brand,
                exampleVehicle.model,
                exampleVehicle.year,
                exampleVehicle.licensePlate,
            ],
        ]);

        const response = await request("/fingerprint/candidates");

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([
            {
                id: exampleVehicle.id,
                brand: exampleVehicle.brand,
                model: exampleVehicle.model,
                year: exampleVehicle.year,
                licensePlate: exampleVehicle.licensePlate,
            },
        ]);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);

        const { sql, values } = getFirstQuery();
        expect(sql).toContain(
            'select "id", "brand", "model", "year", "license_plate" from "vehicle"',
        );
        expect(sql).toContain('"vehicle"."fingerprint" is null');
        expect(values).toEqual([]);
    });

    it("returns an empty list when every vehicle is already paired", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockRows("select", []);

        const response = await request("/fingerprint/candidates");

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([]);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });

    it("returns 500 when the candidate lookup fails", async () => {
        getSessionMock.mockResolvedValue(session);
        dbClientQueryMock.mockRejectedValueOnce(
            new Error("database unavailable"),
        );

        const response = await request("/fingerprint/candidates");

        expect(response.status).toBe(500);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });
});

describe("POST /fleet/fingerprint/pair", () => {
    it("pairs a fingerprint with an unpaired vehicle", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockRowCount("update", 1);

        const response = await jsonRequest("/fingerprint/pair", "POST", {
            vehicleId,
            fingerprint: "device-123",
        });

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            message: "Fingerprint paired successfully",
        });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);

        const { sql, values } = getFirstQuery();
        expect(sql).toContain(
            'update "vehicle" set "fingerprint" = $1, "updated_at" = $2',
        );
        expect(sql).toContain('"vehicle"."id" = $3');
        expect(sql).toContain('"vehicle"."fingerprint" is null');
        expect(values).toHaveLength(3);
        expect(values[0]).toBe("device-123");
        expect(values[1]).toEqual(expect.any(String));
        expect(values[2]).toBe(vehicleId);
    });

    it("returns 404 when the vehicle is missing or already paired", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await jsonRequest("/fingerprint/pair", "POST", {
            vehicleId,
            fingerprint: "device-123",
        });

        expect(response.status).toBe(404);
        expect(await response.json()).toEqual({
            error: "Vehicle not found or already paired",
        });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });

    it.each([
        ["a missing vehicle id", { fingerprint: "device-123" }],
        [
            "a non-UUID vehicle id",
            { vehicleId: "not-a-uuid", fingerprint: "device-123" },
        ],
        ["a missing fingerprint", { vehicleId }],
        ["a non-string fingerprint", { vehicleId, fingerprint: 123 }],
    ])("returns 422 for %s without updating the database", async (_, body) => {
        getSessionMock.mockResolvedValue(session);

        const response = await jsonRequest("/fingerprint/pair", "POST", body);

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("returns 500 when the update fails", async () => {
        getSessionMock.mockResolvedValue(session);
        dbClientQueryMock.mockRejectedValueOnce(
            new Error("database unavailable"),
        );

        const response = await jsonRequest("/fingerprint/pair", "POST", {
            vehicleId,
            fingerprint: "device-123",
        });

        expect(response.status).toBe(500);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });
});
