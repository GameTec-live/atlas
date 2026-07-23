import { afterEach, beforeEach, describe, expect, it, mock } from "bun:test";
import { existsSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { Elysia } from "elysia";
import { getSessionMock, resetAuthMocks, session } from "../mocks/auth";

mock.module("@/env", () => ({
    env: {
        CONFIG_FILE: undefined,
    },
}));

const { config, configApp } = await import("@/src/config");
const app = new Elysia().use(configApp);

const adminSession = {
    ...session,
    user: {
        ...session.user,
        role: "admin",
    },
};

describe("config API", () => {
    let originalConfig: string | undefined;

    beforeEach(async () => {
        resetAuthMocks();
        originalConfig = existsSync(config.$path)
            ? readFileSync(config.$path, "utf8")
            : undefined;
        await config.$reload();
    });

    afterEach(async () => {
        if (originalConfig === undefined) {
            rmSync(config.$path, { force: true });
        } else {
            writeFileSync(config.$path, originalConfig, "utf8");
        }
        await config.$reload();
    });

    const request = (method = "GET", body?: unknown) =>
        app.handle(
            new Request("http://localhost/config", {
                method,
                headers:
                    body === undefined
                        ? undefined
                        : { "content-type": "application/json" },
                body: body === undefined ? undefined : JSON.stringify(body),
            }),
        );

    it("requires an admin session", async () => {
        expect((await request()).status).toBe(401);

        getSessionMock.mockResolvedValue(session);
        expect((await request()).status).toBe(403);
    });

    it("returns the validated configuration", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await request();

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual(config.$snapshot());
    });

    it("updates the configuration and writes it to the backing file", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await request("PUT", {
            routing: { defaultLanguage: "de-AT" },
        });

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            routing: { defaultLanguage: "de-AT" },
        });
        expect(Bun.TOML.parse(readFileSync(config.$path, "utf8"))).toEqual({
            routing: { defaultLanguage: "de-AT" },
        });
    });

    it("rejects invalid updates without changing the configuration", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        const configBeforeRequest = config.$snapshot();
        const fileBeforeRequest = originalConfig;

        const response = await request("PUT", {
            routing: { defaultLanguage: "english" },
        });

        expect(response.status).toBe(422);
        expect(config.$snapshot()).toEqual(configBeforeRequest);
        expect(
            existsSync(config.$path)
                ? readFileSync(config.$path, "utf8")
                : undefined,
        ).toBe(fileBeforeRequest);
    });
});
