import { beforeEach, describe, expect, it } from "bun:test";
import { Elysia } from "elysia";
import { dbClientQueryMock, exampleData, resetDbMocks } from "../mocks/db";

const { unauthed } = await import("@/src/unprotected");
const app = new Elysia().use(unauthed);

describe("GET /unprotected", () => {
    beforeEach(() => {
        resetDbMocks();
    });

    it("returns vehicles from Drizzle without requiring a session", async () => {
        const response = await app.handle(
            new Request("http://localhost/unprotected/"),
        );

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual(
            exampleData.vehicle.map((vehicle) => ({
                ...vehicle,
                createdAt: vehicle.createdAt.toISOString(),
                updatedAt: vehicle.updatedAt.toISOString(),
            })),
        );
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock.mock.calls[0]?.[0]).toMatchObject({
            text: expect.stringContaining('from "vehicle"'),
        });
    });
});
