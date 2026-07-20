import { beforeEach, describe, expect, it } from "bun:test";
import { Elysia } from "elysia";
import { getSessionMock, resetAuthMocks, session } from "../mocks/auth";
import { dbClientQueryMock, exampleData, resetDbMocks } from "../mocks/db";

const { authed } = await import("@/src/protected");
const app = new Elysia().use(authed);

describe("GET /protected", () => {
    beforeEach(() => {
        resetAuthMocks();
        resetDbMocks();
    });

    it("returns 401 when there is no Better Auth session", async () => {
        const response = await app.handle(
            new Request("http://localhost/protected/"),
        );

        expect(response.status).toBe(401);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("returns vehicles for an authenticated session", async () => {
        getSessionMock.mockResolvedValue(session);

        const response = await app.handle(
            new Request("http://localhost/protected/", {
                headers: { authorization: "Bearer test-token" },
            }),
        );

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual(
            exampleData.vehicle.map((vehicle) => ({
                ...vehicle,
                createdAt: vehicle.createdAt.toISOString(),
                updatedAt: vehicle.updatedAt.toISOString(),
            })),
        );
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock).toHaveBeenCalledTimes(1);
        expect(dbClientQueryMock.mock.calls[0]?.[0]).toMatchObject({
            text: expect.stringContaining('from "vehicle"'),
        });
    });
});
