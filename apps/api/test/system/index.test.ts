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
let applyUpdateResponder:
    | ((path: string) => Response | Promise<Response>)
    | undefined;

const managementRequestMock = mock(async (path: string, init?: RequestInit) => {
    managementRequests.push({ path, init });
    return (
        managementResponder?.(path, init) ??
        Response.json({ status: "ok", path })
    );
});
const applyUpdateMock = mock(async (path: string) => {
    const response = applyUpdateResponder?.(path);
    stagedPathExisted = existsSync(path);
    appliedBytes = await Bun.file(path).bytes();
    return (
        (await response) ??
        Response.json({ status: "rebooting_into_candidate" }, { status: 202 })
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
    applyUpdateResponder = undefined;
    managementRequestMock.mockClear();
    applyUpdateMock.mockClear();
    envMock.DATA_STORAGE_PATH = undefined;
    envMock.OS_MANAGEMENT_SOCKET = undefined;
});

describe("system availability and authentication", () => {
    it("requires an admin session", async () => {
        expect((await request(app, "/")).status).toBe(401);
        getSessionMock.mockResolvedValue(session);
        expect((await request(app, "/")).status).toBe(403);
    });

    it("reports unavailable management without probing outside AtlasOS", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const statusResponse = await request(app, "/");
        expect(statusResponse.status).toBe(200);
        expect(await statusResponse.json()).toEqual({
            management: {
                available: false,
                reason: "Atlas OS management is unavailable",
            },
        });
        expect(managementRequests).toEqual([]);
    });

    it("reports an unavailable AtlasOS management service", async () => {
        envMock.OS_MANAGEMENT_SOCKET = "/run/atlas-management/api.sock";
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
        expect(managementRequests[0]?.path).toBe("/healthz");

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
        managementResponder = () =>
            Response.json({
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
        expect(managementRequests).toHaveLength(1);
        expect(managementRequests[0]?.path).toBe(
            "/api/v1/connections/remote-access/cloudflare-tunnel",
        );
        expect(JSON.parse(String(managementRequests[0]?.init?.body))).toEqual({
            token: "secret",
            origin: "https://atlas.example.com",
        });
    });

    it("provisions remote access when no origin change is needed", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await jsonRequest(
            app,
            "/connections/remote-access/cloudflare-tunnel",
            "PUT",
            { token: "secret" },
        );

        expect(response.status).toBe(200);
        expect(JSON.parse(String(managementRequests[0]?.init?.body))).toEqual({
            token: "secret",
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
            let bodyAcquired = false;
            const uploadRequest = new Request(
                "http://localhost/system/update/upload",
                {
                    method: "POST",
                    headers: {
                        authorization: "Bearer test-token",
                        "content-type": "application/octet-stream",
                    },
                },
            );
            Object.defineProperty(uploadRequest, "body", {
                get() {
                    bodyAcquired = true;
                    throw new Error("pending update acquired the request body");
                },
            });

            const response = await app.handle(uploadRequest);

            expect(response.status).toBe(409);
            expect(await response.json()).toEqual({
                error: {
                    code: "update_pending",
                    message: "An Atlas OS update is already pending",
                },
            });
            expect(bodyAcquired).toBe(false);
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

    it("uploads firmware in bounded chunks before starting installation", async () => {
        const stagingDirectory = mkdtempSync(join(tmpdir(), "atlas-update-"));
        try {
            envMock.DATA_STORAGE_PATH = stagingDirectory;
            getSessionMock.mockResolvedValue(adminSession);
            const update = new Uint8Array([0, 1, 2, 3, 4]);
            let finishInstallation: (response: Response) => void = () =>
                undefined;
            applyUpdateResponder = () =>
                new Promise((resolve) => {
                    finishInstallation = resolve;
                });

            const startResponse = await jsonRequest(
                app,
                "/update/upload/start",
                "POST",
                { size: update.byteLength },
            );
            expect(startResponse.status).toBe(201);
            const started = (await startResponse.json()) as {
                uploadId: string;
                chunkSize: number;
            };
            expect(started.chunkSize).toBeGreaterThan(0);

            const uploadChunk = (start: number, end: number) =>
                request(app, `/update/upload/${started.uploadId}`, {
                    method: "PUT",
                    headers: {
                        "content-type": "application/octet-stream",
                        "content-range": `bytes ${start}-${end - 1}/${update.byteLength}`,
                    },
                    body: update.slice(start, end),
                });

            const firstChunk = await uploadChunk(0, 2);
            expect(firstChunk.status).toBe(200);
            expect(await firstChunk.json()).toEqual({ received: 2 });
            const secondChunk = await uploadChunk(2, update.byteLength);
            expect(secondChunk.status).toBe(200);
            expect(await secondChunk.json()).toEqual({ received: 5 });

            const installResponse = await request(
                app,
                `/update/upload/${started.uploadId}/install`,
                { method: "POST" },
            );
            expect(installResponse.status).toBe(202);
            expect(await installResponse.json()).toEqual({
                status: "installing",
            });
            expect(applyUpdateMock).toHaveBeenCalledTimes(1);
            finishInstallation(
                Response.json(
                    { status: "rebooting_into_candidate" },
                    { status: 202 },
                ),
            );
            await applyUpdateMock.mock.results[0]?.value;
            for (
                let attempt = 0;
                attempt < 100 &&
                readdirSync(join(stagingDirectory, "system-updates")).length >
                    0;
                attempt++
            ) {
                await Bun.sleep(1);
            }

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
