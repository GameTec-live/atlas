import { beforeEach, describe, expect, it, mock, spyOn } from "bun:test";
import {
    existsSync,
    mkdirSync,
    mkdtempSync,
    readdirSync,
    rmSync,
    writeFileSync,
} from "node:fs";
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

const { fromLatestGitHub, reconcileStagedUploads } = await import(
    "@/src/system/firmware"
);
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
    it("reconciles orphaned uploads as best-effort cleanup", async () => {
        const storageDirectory = mkdtempSync(join(tmpdir(), "atlas-update-"));
        const consoleError = spyOn(console, "error").mockImplementation(
            () => undefined,
        );
        try {
            envMock.DATA_STORAGE_PATH = storageDirectory;
            const updateDirectory = join(storageDirectory, "system-updates");
            mkdirSync(updateDirectory);
            writeFileSync(
                join(
                    updateDirectory,
                    "update-00000000-0000-4000-8000-000000000000.tar.zst",
                ),
                "stale",
            );
            const undeletableName =
                "update-11111111-1111-4111-8111-111111111111.tar.zst";
            mkdirSync(join(updateDirectory, undeletableName));
            writeFileSync(join(updateDirectory, "keep.tar.zst"), "keep");

            await reconcileStagedUploads();

            expect(readdirSync(updateDirectory).sort()).toEqual(
                ["keep.tar.zst", undeletableName].sort(),
            );
            expect(consoleError).toHaveBeenCalledTimes(1);
            expect(String(consoleError.mock.calls[0]?.[0])).toContain(
                undeletableName,
            );
        } finally {
            consoleError.mockRestore();
            rmSync(storageDirectory, { recursive: true, force: true });
        }
    });

    it("rejects an upload when an update is already pending", async () => {
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
            const response = await jsonRequest(app, "/update/upload", "POST", {
                size: 3,
            });

            expect(response.status).toBe(409);
            expect(await response.json()).toEqual({
                error: {
                    code: "update_pending",
                    message: "An Atlas OS update is already pending",
                },
            });
            expect(applyUpdateMock).not.toHaveBeenCalled();
            expect(existsSync(join(stagingDirectory, "system-updates"))).toBe(
                false,
            );
        } finally {
            rmSync(stagingDirectory, { recursive: true, force: true });
        }
    });

    it("allows only one upload at a time", async () => {
        const stagingDirectory = mkdtempSync(join(tmpdir(), "atlas-update-"));
        try {
            envMock.DATA_STORAGE_PATH = stagingDirectory;
            getSessionMock.mockResolvedValue(adminSession);
            const upload = () =>
                jsonRequest(app, "/update/upload", "POST", { size: 3 });

            const responses = await Promise.all([upload(), upload()]);

            expect(responses.map(({ status }) => status).sort()).toEqual([
                201, 409,
            ]);
            const accepted = responses.find(({ status }) => status === 201);
            if (!accepted) throw new Error("Expected one accepted upload");
            const { uploadId } = (await accepted.json()) as {
                uploadId: string;
            };
            expect(
                (
                    await request(app, `/update/upload/${uploadId}`, {
                        method: "DELETE",
                    })
                ).status,
            ).toBe(200);
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

            const startResponse = await jsonRequest(
                app,
                "/update/upload",
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
                        "upload-offset": String(start),
                    },
                    body: update.slice(start, end),
                });

            const firstChunk = await uploadChunk(0, 2);
            expect(firstChunk.status).toBe(200);
            expect(await firstChunk.json()).toEqual({ received: 2 });
            const secondChunk = await uploadChunk(2, update.byteLength);
            expect(secondChunk.status).toBe(202);
            expect(await secondChunk.json()).toEqual({
                status: "rebooting_into_candidate",
            });
            expect(applyUpdateMock).toHaveBeenCalledTimes(1);

            expect(appliedBytes).toEqual(update);
            expect(
                readdirSync(join(stagingDirectory, "system-updates")),
            ).toEqual([]);
        } finally {
            rmSync(stagingDirectory, { recursive: true, force: true });
        }
    });

    it("forwards a management rejection after a completed upload", async () => {
        const stagingDirectory = mkdtempSync(join(tmpdir(), "atlas-update-"));
        try {
            envMock.DATA_STORAGE_PATH = stagingDirectory;
            getSessionMock.mockResolvedValue(adminSession);
            applyUpdateResponder = () =>
                Response.json(
                    {
                        error: {
                            code: "management_unavailable",
                            message: "Atlas OS management is unavailable",
                        },
                    },
                    { status: 503 },
                );

            const startResponse = await jsonRequest(
                app,
                "/update/upload",
                "POST",
                { size: 1 },
            );
            const { uploadId } = (await startResponse.json()) as {
                uploadId: string;
            };
            const chunkResponse = await request(
                app,
                `/update/upload/${uploadId}`,
                {
                    method: "PUT",
                    headers: {
                        "content-type": "application/octet-stream",
                        "upload-offset": "0",
                    },
                    body: new Uint8Array([1]),
                },
            );
            expect(chunkResponse.status).toBe(503);
            expect(await chunkResponse.json()).toEqual({
                error: {
                    code: "management_unavailable",
                    message: "Atlas OS management is unavailable",
                },
            });
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
