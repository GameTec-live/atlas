import { afterEach, describe, expect, it } from "bun:test";
import { Elysia } from "elysia";
import "../mocks/auth";
import "../mocks/db";
import { envMock } from "../mocks/env";

const { setup } = await import("@/src/setup");
const app = new Elysia().use(setup);

const getSetupStatus = async () => {
    const response = await app.handle(new Request("http://localhost/setup"));
    expect(response.status).toBe(200);
    return response.json();
};

afterEach(() => {
    envMock.OS_MANAGEMENT_SOCKET = undefined;
});

describe("setup capabilities", () => {
    it("disables system management without an AtlasOS socket", async () => {
        expect(await getSetupStatus()).toMatchObject({
            capabilities: { systemManagement: false },
        });
    });

    it("enables system management when AtlasOS provides its socket", async () => {
        envMock.OS_MANAGEMENT_SOCKET = "/run/atlas-management/api.sock";

        expect(await getSetupStatus()).toMatchObject({
            capabilities: { systemManagement: true },
        });
    });
});
