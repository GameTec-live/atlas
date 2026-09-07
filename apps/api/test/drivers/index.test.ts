import { beforeEach, describe, expect, it } from "bun:test";
import { Elysia } from "elysia";
import { getSessionMock, resetAuthMocks, session } from "../mocks/auth";
import { dbClientQueryMock, resetDbMocks, setDbMockRows } from "../mocks/db";

const { drivers } = await import("@/src/drivers");
const app = new Elysia().use(drivers);
const request = () => app.handle(new Request("http://localhost/drivers/"));

beforeEach(() => {
    resetAuthMocks();
    resetDbMocks();
});

describe("GET /drivers/", () => {
    it("requires authentication", async () => {
        const response = await request();

        expect(response.status).toBe(401);
        expect(dbClientQueryMock).not.toHaveBeenCalled();
    });

    it("allows non-admins to get all drivers and their role claim status", async () => {
        getSessionMock.mockResolvedValue(session);
        setDbMockRows("select", [
            ["driver-1", "Active Driver", true],
            ["driver-2", "Inactive Driver", false],
        ]);

        const response = await request();

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual([
            { driverId: "driver-1", name: "Active Driver", signedOn: true },
            { driverId: "driver-2", name: "Inactive Driver", signedOn: false },
        ]);

        // Keep the date condition on the join so unclaimed drivers remain included.
        expect(dbClientQueryMock.mock.calls[0]?.[0]).toMatchObject({
            text: expect.stringContaining(
                'from "user" left join "role" on (("role"."driver_id" = "user"."id") and ("role"."date" = current_date))',
            ),
        });
    });
});
