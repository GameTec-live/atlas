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
    year: "2025-01-01T00:00:00.000Z",
    licensePlate: "ATLAS-2",
    odometer: 0,
    fuelLevel: 100,
    maintenanceEvery: 0,
    assessmentMonth: "2026-08-01T00:00:00.000Z",
    smartSupport: false,
};

const serializedVehicle = {
    ...exampleVehicle,
    year: exampleVehicle.year.toISOString(),
    assessmentMonth: exampleVehicle.assessmentMonth.toISOString(),
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
        ["POST", "/vehicles", vehicleBody],
        ["PUT", `/vehicles/${vehicleId}`, { model: "Crafter" }],
        ["DELETE", `/vehicles/${vehicleId}`, undefined],
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
});

describe("GET /fleet/vehicles", () => {
    it("returns every vehicle with its latest maintenance record", async () => {
        getSessionMock.mockResolvedValue(session);
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
        getSessionMock.mockResolvedValue(session);
        setDbMockRows("select", []);

        const response = await request("/vehicles");

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([]);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });
});

describe("GET /fleet/vehicles/:id", () => {
    it("returns the vehicle and its latest maintenance record", async () => {
        getSessionMock.mockResolvedValue(session);
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
        getSessionMock.mockResolvedValue(session);
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
        getSessionMock.mockResolvedValue(session);
        setDbMockRows("select", []);

        const response = await request(`/vehicles/${vehicleId}`);

        expect(response.status).toBe(404);
        expect(await response.json()).toEqual({ error: "Vehicle not found" });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });

    it("returns 422 for a non-UUID id without querying the database", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await request("/vehicles/not-a-uuid");

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });
});

describe("POST /fleet/vehicles", () => {
    it("creates a vehicle using every supported field", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await jsonRequest("/vehicles", "POST", vehicleBody);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            message: "Vehicle created successfully",
        });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);

        const { sql, values } = getFirstQuery();
        expect(sql).toContain('insert into "vehicle"');
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
        getSessionMock.mockResolvedValue(session);
        const { odometer, fuelLevel, smartSupport, ...requiredBody } =
            vehicleBody;

        const response = await jsonRequest("/vehicles", "POST", requiredBody);

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            message: "Vehicle created successfully",
        });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });

    it.each([
        ["a missing brand", { ...vehicleBody, brand: undefined }],
        ["an invalid year", { ...vehicleBody, year: "not-a-date" }],
        ["a negative odometer", { ...vehicleBody, odometer: -1 }],
        ["a fuel level below zero", { ...vehicleBody, fuelLevel: -0.01 }],
        ["a fuel level above 100", { ...vehicleBody, fuelLevel: 100.01 }],
        [
            "a negative maintenance interval",
            { ...vehicleBody, maintenanceEvery: -1 },
        ],
        [
            "an invalid assessment month",
            { ...vehicleBody, assessmentMonth: "not-a-date" },
        ],
    ])("returns 422 for %s", async (_description, body) => {
        getSessionMock.mockResolvedValue(session);

        const response = await jsonRequest("/vehicles", "POST", body);

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });
});

describe("PUT /fleet/vehicles/:id", () => {
    it("updates the supplied vehicle fields and automatic timestamp", async () => {
        getSessionMock.mockResolvedValue(session);
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
        getSessionMock.mockResolvedValue(session);

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
            { assessmentMonth: "not-a-date" },
        ],
    ])("returns 422 for %s", async (_description, path, body) => {
        getSessionMock.mockResolvedValue(session);

        const response = await jsonRequest(path, "PUT", body);

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });
});

describe("DELETE /fleet/vehicles/:id", () => {
    it("deletes an existing vehicle", async () => {
        getSessionMock.mockResolvedValue(session);
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
        getSessionMock.mockResolvedValue(session);

        const response = await request(`/vehicles/${vehicleId}`, {
            method: "DELETE",
        });

        expect(response.status).toBe(404);
        expect(await response.json()).toEqual({ error: "Vehicle not found" });
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
    });

    it("returns 422 for a non-UUID id without querying the database", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await request("/vehicles/not-a-uuid", {
            method: "DELETE",
        });

        expect(response.status).toBe(422);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });
});
