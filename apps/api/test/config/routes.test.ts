import { afterEach, beforeEach, describe, expect, it, mock } from "bun:test";
import {
    existsSync,
    mkdirSync,
    mkdtempSync,
    readdirSync,
    readFileSync,
    rmSync,
    utimesSync,
    writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { Elysia } from "elysia";
import { MAX_LOGO_SIZE } from "@/src/config/logo";
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
            routing: { defaultLanguage: "en-US-x-pirate" },
        });

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            routing: { defaultLanguage: "en-US-x-pirate" },
            dispatchers: { max: 1 },
            pricing: { pricePerKilometer: 0 },
            storage: { dataLocation: "./data", logoName: "logo" },
        });
        expect(Bun.TOML.parse(readFileSync(config.$path, "utf8"))).toEqual({
            routing: { defaultLanguage: "en-US-x-pirate" },
            dispatchers: { max: 1 },
            pricing: { pricePerKilometer: 0 },
            storage: { dataLocation: "./data", logoName: "logo" },
        });
    });

    it("rejects invalid updates without changing the configuration", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        const configBeforeRequest = config.$snapshot();
        const fileBeforeRequest = originalConfig;

        const response = await request("PUT", {
            routing: { defaultLanguage: "de-AT" },
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

describe("logo API", () => {
    let storageDirectory: string;

    beforeEach(async () => {
        resetAuthMocks();
        storageDirectory = mkdtempSync(join(tmpdir(), "atlas-logo-"));
        await config.$set("storage", {
            dataLocation: storageDirectory,
            logoName: "logo",
        });
    });

    afterEach(async () => {
        rmSync(storageDirectory, { recursive: true, force: true });
        await config.$reload();
    });

    const getLogo = () =>
        app.handle(new Request("http://localhost/config/logo"));

    const deleteLogo = () =>
        app.handle(
            new Request("http://localhost/config/logo", { method: "DELETE" }),
        );

    const putLogo = (body?: Uint8Array<ArrayBuffer>, contentType?: string) => {
        const headers = new Headers();
        if (contentType !== undefined) {
            headers.set("content-type", contentType);
        }

        return app.handle(
            new Request("http://localhost/config/logo", {
                method: "PUT",
                headers,
                body,
            }),
        );
    };

    it("returns 404 when no logo has been uploaded", async () => {
        const response = await getLogo();

        expect(response.status).toBe(404);
        expect(await response.text()).toBe("Logo file not found");
    });

    it("serves the newest logo without requiring authentication", async () => {
        const olderLogo = join(storageDirectory, "logo.png");
        const newerLogo = join(storageDirectory, "logo.svg");
        writeFileSync(olderLogo, "old logo");
        writeFileSync(newerLogo, "<svg>new logo</svg>");
        utimesSync(olderLogo, new Date(1_000), new Date(1_000));
        utimesSync(newerLogo, new Date(2_000), new Date(2_000));

        const response = await getLogo();

        expect(response.status).toBe(200);
        expect(response.headers.get("content-type")).toBe("image/svg+xml");
        expect(await response.text()).toBe("<svg>new logo</svg>");
        expect(getSessionMock).not.toHaveBeenCalled();
    });

    it("ignores similarly named directories and unrelated files", async () => {
        mkdirSync(join(storageDirectory, "logo.png"));
        writeFileSync(join(storageDirectory, "not-logo.png"), "not a logo");
        writeFileSync(join(storageDirectory, "logo.png.bak"), "backup");

        const response = await getLogo();

        expect(response.status).toBe(404);
    });

    it("requires an admin session to upload a logo", async () => {
        const unauthenticatedResponse = await putLogo(
            new Uint8Array([1]),
            "image/png",
        );
        expect(unauthenticatedResponse.status).toBe(401);

        getSessionMock.mockResolvedValue(session);
        const nonAdminResponse = await putLogo(
            new Uint8Array([1]),
            "image/png",
        );
        expect(nonAdminResponse.status).toBe(403);
        expect(readdirSync(storageDirectory)).toEqual([]);
    });

    it("requires an admin session to delete a logo", async () => {
        writeFileSync(join(storageDirectory, "logo.png"), "existing logo");

        expect((await deleteLogo()).status).toBe(401);

        getSessionMock.mockResolvedValue(session);
        expect((await deleteLogo()).status).toBe(403);
        expect(readdirSync(storageDirectory)).toEqual(["logo.png"]);
    });

    it("deletes every stored logo format without deleting unrelated files", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        writeFileSync(join(storageDirectory, "logo.png"), "png logo");
        writeFileSync(join(storageDirectory, "logo.svg"), "svg logo");
        writeFileSync(join(storageDirectory, "logo.png.bak"), "backup");
        writeFileSync(join(storageDirectory, "not-logo.png"), "unrelated");

        const response = await deleteLogo();

        expect(response.status).toBe(200);
        expect(readdirSync(storageDirectory).sort()).toEqual([
            "logo.png.bak",
            "not-logo.png",
        ]);
        expect((await getLogo()).status).toBe(404);
    });

    it("allows deleting a logo when none is installed", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await deleteLogo();

        expect(response.status).toBe(200);
        expect(readdirSync(storageDirectory)).toEqual([]);
    });

    it.each([
        undefined,
        "application/json",
        "image/pngish",
    ])("rejects the unsupported content type %p", async (contentType) => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await putLogo(new Uint8Array([1, 2, 3]), contentType);

        expect(response.status).toBe(415);
        expect(await response.text()).toBe("Unsupported logo image type");
        expect(readdirSync(storageDirectory)).toEqual([]);
    });

    it("rejects an empty logo", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        writeFileSync(join(storageDirectory, "logo.webp"), "existing logo");

        const response = await putLogo(new Uint8Array(), "image/png");

        expect(response.status).toBe(400);
        expect(await response.text()).toBe("Logo file is empty");
        expect(readdirSync(storageDirectory)).toEqual(["logo.webp"]);
        expect(readFileSync(join(storageDirectory, "logo.webp"), "utf8")).toBe(
            "existing logo",
        );
    });

    it("rejects an oversized logo without persisting it", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await putLogo(
            new Uint8Array(MAX_LOGO_SIZE + 1),
            "image/png",
        );

        expect(response.status).toBe(413);
        expect(await response.text()).toBe(
            `Logo exceeds the ${MAX_LOGO_SIZE}-byte size limit`,
        );
        expect(readdirSync(storageDirectory)).toEqual([]);
    });

    it("creates a missing storage directory", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        rmSync(storageDirectory, { recursive: true });

        const response = await putLogo(new Uint8Array([1, 2, 3]), "image/png");

        expect(response.status).toBe(200);
        expect(readdirSync(storageDirectory)).toEqual(["logo.png"]);
    });

    it.each([
        ["image/avif", "avif"],
        ["image/bmp", "bmp"],
        ["image/gif", "gif"],
        ["image/jpeg", "jpg"],
        ["image/png", "png"],
        ["image/svg+xml", "svg"],
        ["image/vnd.microsoft.icon", "ico"],
        ["image/webp", "webp"],
        ["image/x-icon", "ico"],
    ])("stores %s uploads with the .%s extension", async (contentType, extension) => {
        getSessionMock.mockResolvedValue(adminSession);
        const bytes = new Uint8Array([0, 1, 2, 255]);

        const response = await putLogo(bytes, contentType);

        expect(response.status).toBe(200);
        expect(readdirSync(storageDirectory)).toEqual([`logo.${extension}`]);
        expect(
            readFileSync(join(storageDirectory, `logo.${extension}`)),
        ).toEqual(Buffer.from(bytes));
    });

    it("normalizes content types with casing and parameters", async () => {
        getSessionMock.mockResolvedValue(adminSession);

        const response = await putLogo(
            new Uint8Array([1, 2, 3]),
            "IMAGE/PNG; charset=binary",
        );

        expect(response.status).toBe(200);
        expect(readdirSync(storageDirectory)).toEqual(["logo.png"]);
    });

    it("replaces old logo formats without deleting unrelated files", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        writeFileSync(join(storageDirectory, "logo.png"), "old png");
        writeFileSync(join(storageDirectory, "logo.webp"), "old webp");
        writeFileSync(join(storageDirectory, "logo-backup.png"), "backup");
        writeFileSync(join(storageDirectory, "logo.png.bak"), "backup");
        mkdirSync(join(storageDirectory, "logo.assets"));

        const response = await putLogo(new Uint8Array([4, 5, 6]), "image/jpeg");

        expect(response.status).toBe(200);
        expect(readdirSync(storageDirectory).sort()).toEqual([
            "logo-backup.png",
            "logo.assets",
            "logo.jpg",
            "logo.png.bak",
        ]);
        expect(readFileSync(join(storageDirectory, "logo.jpg"))).toEqual(
            Buffer.from([4, 5, 6]),
        );
    });

    it("serializes concurrent uploads in different formats", async () => {
        getSessionMock.mockResolvedValue(adminSession);
        const png = new TextEncoder().encode("png logo");
        const svg = new TextEncoder().encode("svg logo");

        const [pngResponse, svgResponse] = await Promise.all([
            putLogo(png, "image/png"),
            putLogo(svg, "image/svg+xml"),
        ]);

        expect(pngResponse.status).toBe(200);
        expect(svgResponse.status).toBe(200);

        const storedLogos = readdirSync(storageDirectory).filter((name) =>
            ["logo.png", "logo.svg"].includes(name),
        );
        expect(storedLogos).toHaveLength(1);

        const downloadResponse = await getLogo();
        expect(downloadResponse.status).toBe(200);
        expect(["png logo", "svg logo"]).toContain(
            await downloadResponse.text(),
        );
    });

    it.each([
        "file",
        "directory",
    ] as const)("recovers an abandoned logo lock %s", async (lockType) => {
        getSessionMock.mockResolvedValue(adminSession);
        const lockPath = join(storageDirectory, ".logo.lock");
        const abandonedAt = new Date(0);

        if (lockType === "file") {
            writeFileSync(lockPath, "legacy lock");
            utimesSync(lockPath, abandonedAt, abandonedAt);
        } else {
            mkdirSync(lockPath);
            const ownerTokenPath = join(lockPath, "owner-abandoned");
            writeFileSync(ownerTokenPath, "");
            utimesSync(ownerTokenPath, abandonedAt, abandonedAt);
        }

        const response = await putLogo(
            new TextEncoder().encode("replacement"),
            "image/png",
        );

        expect(response.status).toBe(200);
        expect(readdirSync(storageDirectory)).toEqual(["logo.png"]);
    });

    it("uses the configured logo name for uploads and downloads", async () => {
        await config.$set("storage", {
            dataLocation: storageDirectory,
            logoName: "company-brand",
        });
        getSessionMock.mockResolvedValue(adminSession);

        const uploadResponse = await putLogo(
            new TextEncoder().encode("brand image"),
            "image/webp",
        );
        const downloadResponse = await getLogo();

        expect(uploadResponse.status).toBe(200);
        expect(readdirSync(storageDirectory)).toEqual(["company-brand.webp"]);
        expect(downloadResponse.status).toBe(200);
        expect(await downloadResponse.text()).toBe("brand image");
    });
});
