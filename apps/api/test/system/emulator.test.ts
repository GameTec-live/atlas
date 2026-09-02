import { beforeEach, describe, expect, it } from "bun:test";
import { EMULATOR_TOKEN, managementEmulator } from "@/src/system/emulator";

const request = (path: string, init: RequestInit = {}) => {
    const headers = new Headers(init.headers);
    headers.set("authorization", `Bearer ${EMULATOR_TOKEN}`);
    return managementEmulator.handle(
        new Request(`http://localhost${path}`, { ...init, headers }),
    );
};

const jsonRequest = (path: string, method: string, body?: unknown) =>
    request(path, {
        method,
        headers:
            body === undefined
                ? undefined
                : { "content-type": "application/json" },
        body: body === undefined ? undefined : JSON.stringify(body),
    });

beforeEach(async () => {
    await jsonRequest("/api/v1/update/rollback", "POST");
    await jsonRequest("/api/v1/ssh/disable", "POST");
});

describe("management emulator", () => {
    it("requires the same bearer authentication as the real service", async () => {
        const response = await managementEmulator.handle(
            new Request("http://localhost/healthz"),
        );

        expect(response.status).toBe(401);
        expect(await response.json()).toEqual({
            error: {
                code: "unauthorized",
                message: "a valid bearer token is required",
            },
        });
    });

    it("moves an uploaded update into the candidate state and rolls it back", async () => {
        const form = new FormData();
        form.set(
            "bundle",
            new File([new Uint8Array([1, 2, 3])], "update.tar.zst"),
        );

        const applyResponse = await request("/api/v1/update", {
            method: "POST",
            body: form,
        });
        const candidate = await request("/api/v1/update");

        expect(applyResponse.status).toBe(202);
        expect(await candidate.json()).toMatchObject({
            update: {
                active: "system_b",
                other: "system_a",
                pending: "system_b",
            },
            monitor: { phase: "monitoring" },
        });
        expect(
            (await request("/api/v1/update", { method: "POST", body: form }))
                .status,
        ).toBe(409);

        await jsonRequest("/api/v1/update/rollback", "POST");
        expect(await (await request("/api/v1/update")).json()).toMatchObject({
            update: { active: "system_a", other: "system_b" },
            monitor: { phase: "idle" },
        });
    });

    it("keeps mutable system settings in memory", async () => {
        await jsonRequest("/api/v1/ssh/enable", "POST");
        await jsonRequest("/api/v1/timezone", "PUT", {
            timezone: "Europe/Vienna",
        });
        await jsonRequest(
            "/api/v1/connections/remote-access/cloudflare-tunnel",
            "PUT",
            {
                token: "emulated-token",
                origin: "https://atlas.example.com",
            },
        );

        expect(await (await request("/api/v1/ssh")).json()).toEqual({
            enabled: true,
        });
        expect(await (await request("/api/v1/timezone")).json()).toEqual({
            timezone: "Europe/Vienna",
        });
        expect(
            await (await request("/api/v1/connections/auth-origins")).json(),
        ).toEqual({ items: ["https://atlas.example.com"], count: 1 });
        expect(
            await (await request("/api/v1/connections/remote-access")).json(),
        ).toMatchObject({
            cloudflareTunnel: { provisioned: true, state: "active" },
        });
    });
});
