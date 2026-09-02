import { beforeEach, describe, expect, it, mock } from "bun:test";
import { existsSync, mkdtempSync, readdirSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { Elysia } from "elysia";
import { getSessionMock, resetAuthMocks, session } from "../mocks/auth";
import { envMock } from "../mocks/env";

const adminSession = {
    ...session,
    user: { ...session.user, role: "admin" },
};

type RecordedRequest = { path: string; init?: RequestInit };
const managementRequests: RecordedRequest[] = [];
let appliedBytes: Uint8Array | undefined;
let stagedPathExisted = false;
let managementResponder:
    | ((path: string, init?: RequestInit) => Response)
    | undefined;

const managementRequestMock = mock(async (path: string, init?: RequestInit) => {
    managementRequests.push({ path, init });
    return (
        managementResponder?.(path, init) ??
        Response.json({ status: "ok", path })
    );
});
const applyUpdateMock = mock(async (path: string) => {
    stagedPathExisted = existsSync(path);
    appliedBytes = await Bun.file(path).bytes();
    return Response.json(
        { status: "rebooting_into_candidate" },
        { status: 202 },
    );
});

mock.module("@/src/system/management", () => ({
    request: managementRequestMock,
    applyUpdate: applyUpdateMock,
    UNAVAILABLE_MESSAGE: "Atlas OS management is unavailable",
}));

const { fromLatestGitHub } = await import("@/src/system/firmware");
const { system } = await import("@/src/system");
const app = new Elysia().use(system);

type TestApp = {
    handle(request: Request): Response | Promise<Response>;
};

const request = (app: TestApp, path: string, init: RequestInit = {}) => {
    const headers = new Headers(init.headers);
    headers.set("authorization", "Bearer test-token");
    return app.handle(
        new Request(`http://localhost/system${path}`, { ...init, headers }),
    );
};

const jsonRequest = (
    app: TestApp,
    path: string,
    method: string,
    body: unknown,
) =>
    request(app, path, {
        method,
        headers: { "content-type": "application/json" },
        body: JSON.stringify(body),
    });

beforeEach(() => {
    resetAuthMocks();
    managementRequests.length = 0;
    appliedBytes = undefined;
    stagedPathExisted = false;
    managementResponder = undefined;
    managementRequestMock.mockClear();
    applyUpdateMock.mockClear();
    envMock.DATA_STORAGE_PATH = undefined;
});

describe("system availability and authentication", () => {
    it("requires an admin session", async () => {
        expect((await request(app, "/")).status).toBe(401);
        getSessionMock.mockResolvedValue(session);
        expect((await request(app, "/")).status).toBe(403);
    });

    it("reports unavailable management without failing in Docker Compose", async () => {
        managementResponder = () =>
            Response.json(
                {
                    error: {
                        code: "management_unavailable",
                        message: "Atlas OS management is unavailable",
                    },
                },
                { status: 503 },
            );
        getSessionMock.mockResolvedValue(adminSession);

        const statusResponse = await request(app, "/");
        expect(statusResponse.status).toBe(200);
        expect(await statusResponse.json()).toEqual({
            management: {
                available: false,
                reason: "Atlas OS management is unavailable",
            },
        });

        const operationResponse = await request(app, "/ssh");
        expect(operationResponse.status).toBe(503);
        expect(await operationResponse.json()).toEqual({
            error: {
                code: "management_unavailable",
                message: "Atlas OS management is unavailable",
            },
        });
    });
});

describe("system management routes", () => {
    it("forwards typed network configuration", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        const uuid = "123e4567-e89b-12d3-a456-426614174000";
        const settings = {
            ipv4: {
                method: "manual",
                addresses: ["192.0.2.10/24"],
                gateway: "192.0.2.1",
                dns: ["192.0.2.53"],
            },
            ipv6: { method: "auto", addresses: [], dns: [] },
        };

        const response = await jsonRequest(
            app,
            `/connections/network-manager/${uuid}/ip`,
            "PUT",
            settings,
        );

        expect(response.status).toBe(200);
        expect(managementRequests).toHaveLength(1);
        expect(managementRequests[0]?.path).toBe(
            `/api/v1/connections/network-manager/${uuid}/ip`,
        );
        expect(managementRequests[0]?.init?.method).toBe("PUT");
        expect(JSON.parse(String(managementRequests[0]?.init?.body))).toEqual(
            settings,
        );
    });

    it("provisions Cloudflare together with its trusted origin", async () => {
        managementResponder = (path) =>
            path.endsWith("auth-origins")
                ? Response.json({
                      items: ["https://atlas.example.com"],
                      count: 1,
                  })
                : Response.json({
                      cloudflareTunnel: { provisioned: true, state: "active" },
                      tailscale: { provisioned: false, state: "inactive" },
                  });
        getSessionMock.mockResolvedValue(adminSession);

        const response = await jsonRequest(
            app,
            "/connections/remote-access/cloudflare-tunnel",
            "PUT",
            { token: "secret", origin: "https://atlas.example.com" },
        );

        expect(response.status).toBe(200);
        expect(managementRequests.map(({ path }) => path)).toEqual([
            "/api/v1/connections/remote-access/cloudflare-tunnel",
            "/api/v1/connections/auth-origins",
        ]);
        expect(JSON.parse(String(managementRequests[0]?.init?.body))).toEqual({
            token: "secret",
        });
        expect(JSON.parse(String(managementRequests[1]?.init?.body))).toEqual({
            origin: "https://atlas.example.com",
        });
    });

    it("rejects remote access without a valid HTTPS auth origin", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await jsonRequest(
            app,
            "/connections/remote-access/cloudflare-tunnel",
            "PUT",
            { token: "secret", origin: "http://atlas.example.com/path" },
        );

        expect(response.status).toBe(422);
        expect(managementRequests).toEqual([]);
    });

    it("removes a newly provisioned connector when origin setup fails", async () => {
        managementResponder = (path, init) => {
            if (path.endsWith("auth-origins")) {
                return Response.json(
                    {
                        error: {
                            code: "origins_failed",
                            message: "invalid origin",
                        },
                    },
                    { status: 400 },
                );
            }
            return Response.json({
                status: init?.method === "DELETE" ? "removed" : "active",
            });
        };
        getSessionMock.mockResolvedValue(adminSession);

        const response = await jsonRequest(
            app,
            "/connections/remote-access/cloudflare-tunnel",
            "PUT",
            { token: "secret", origin: "https://atlas.example.com" },
        );

        expect(response.status).toBe(400);
        expect(managementRequests.at(-1)?.init?.method).toBe("DELETE");
    });
});

describe("firmware updates", () => {
    it("rejects an update already pending in management before staging", async () => {
        const stagingDirectory = mkdtempSync(join(tmpdir(), "atlas-update-"));
        try {
            envMock.DATA_STORAGE_PATH = stagingDirectory;
            getSessionMock.mockResolvedValue(adminSession);
            managementResponder = (path) =>
                path === "/api/v1/update"
                    ? Response.json({
                          update: {
                              active: "system_b",
                              other: "system_a",
                              pending: "system_b",
                          },
                          monitor: { phase: "monitoring" },
                      })
                    : Response.json({ status: "ok" });
            let cancelled = false;

            const response = await request(app, "/update/upload", {
                method: "POST",
                headers: { "content-type": "application/octet-stream" },
                body: new ReadableStream({
                    cancel() {
                        cancelled = true;
                    },
                }),
            });

            expect(response.status).toBe(409);
            expect(await response.json()).toEqual({
                error: {
                    code: "update_pending",
                    message: "An Atlas OS update is already pending",
                },
            });
            expect(cancelled).toBe(true);
            expect(applyUpdateMock).not.toHaveBeenCalled();
            expect(existsSync(join(stagingDirectory, "system-updates"))).toBe(
                false,
            );
        } finally {
            rmSync(stagingDirectory, { recursive: true, force: true });
        }
    });

    it("reserves staging so concurrent uploads cannot both consume disk", async () => {
        const stagingDirectory = mkdtempSync(join(tmpdir(), "atlas-update-"));
        try {
            envMock.DATA_STORAGE_PATH = stagingDirectory;
            getSessionMock.mockResolvedValue(adminSession);
            const upload = () =>
                request(app, "/update/upload", {
                    method: "POST",
                    headers: { "content-type": "application/octet-stream" },
                    body: new Uint8Array([1, 2, 3]),
                });

            const responses = await Promise.all([upload(), upload()]);

            expect(responses.map(({ status }) => status).sort()).toEqual([
                202, 409,
            ]);
            expect(applyUpdateMock).toHaveBeenCalledTimes(1);
        } finally {
            rmSync(stagingDirectory, { recursive: true, force: true });
        }
    });

    it("streams raw uploads through a temporary disk file and removes it", async () => {
        const stagingDirectory = mkdtempSync(join(tmpdir(), "atlas-update-"));
        try {
            envMock.DATA_STORAGE_PATH = stagingDirectory;
            getSessionMock.mockResolvedValue(adminSession);
            const update = new Uint8Array([0, 1, 2, 3, 255]);

            const response = await request(app, "/update/upload", {
                method: "POST",
                headers: { "content-type": "application/octet-stream" },
                body: new ReadableStream({
                    start(controller) {
                        controller.enqueue(update.slice(0, 2));
                        controller.enqueue(update.slice(2));
                        controller.close();
                    },
                }),
            });

            expect(response.status).toBe(202);
            expect(stagedPathExisted).toBe(true);
            expect(appliedBytes).toEqual(update);
            expect(
                readdirSync(join(stagingDirectory, "system-updates")),
            ).toEqual([]);
        } finally {
            rmSync(stagingDirectory, { recursive: true, force: true });
        }
    });

    it("selects the update asset from an overridden GitHub repository", async () => {
        const stagingDirectory = mkdtempSync(join(tmpdir(), "atlas-update-"));
        const originalFetch = globalThis.fetch;
        try {
            envMock.DATA_STORAGE_PATH = stagingDirectory;
            const fetched: string[] = [];
            const update = new Uint8Array([4, 5, 6]);
            const fetchMock = (async (input: string | URL | Request) => {
                const url = input.toString();
                fetched.push(url);
                if (url.includes("api.github.com")) {
                    return Response.json({
                        assets: [
                            {
                                name: "atlas-rpi5-v2.0.0-update.tar.zst",
                                browser_download_url:
                                    "https://downloads.example/update.tar.zst",
                            },
                        ],
                    });
                }
                return new Response(update);
            }) as typeof fetch;
            globalThis.fetch = fetchMock;
            const response = await fromLatestGitHub("example/atlas-fork");

            expect(response.status).toBe(202);
            expect(fetched).toEqual([
                "https://api.github.com/repos/example/atlas-fork/releases/latest",
                "https://downloads.example/update.tar.zst",
            ]);
            expect(appliedBytes).toEqual(update);
        } finally {
            globalThis.fetch = originalFetch;
            rmSync(stagingDirectory, { recursive: true, force: true });
        }
    });
});
