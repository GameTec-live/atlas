import { afterEach, beforeEach, describe, expect, it } from "bun:test";
import {
    existsSync,
    mkdtempSync,
    readFileSync,
    rmSync,
    writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import * as v from "valibot";
import { createConfig } from "@/src/config/provider";

const schema = v.object({
    server: v.optional(
        v.object({
            host: v.optional(v.string(), "localhost"),
            port: v.optional(v.pipe(v.number(), v.integer()), 3000),
        }),
        { host: "localhost", port: 3000 },
    ),
    featureEnabled: v.optional(v.boolean(), false),
});

describe("createConfig", () => {
    let testDirectory: string;
    let configFile: string;

    beforeEach(() => {
        testDirectory = mkdtempSync(join(tmpdir(), "atlas-config-"));
        configFile = join(testDirectory, "config.toml");
    });

    afterEach(() => {
        rmSync(testDirectory, { recursive: true, force: true });
    });

    it("uses schema defaults when config.toml is absent", async () => {
        const config = await createConfig({ schema, configFile });

        expect(config.server).toEqual({
            host: "localhost",
            port: 3000,
        });
        expect(config.featureEnabled).toBe(false);
        expect(config.$path).toBe(configFile);
        expect(existsSync(configFile)).toBe(false);
    });

    it("loads and validates an existing TOML file", async () => {
        writeFileSync(
            configFile,
            [
                "featureEnabled = true",
                "",
                "[server]",
                'host = "api.internal"',
                "port = 8080",
                "",
            ].join("\n"),
        );

        const config = await createConfig({ schema, configFile });

        expect(config.server.host).toBe("api.internal");
        expect(config.server.port).toBe(8080);
        expect(config.featureEnabled).toBe(true);
    });

    it("rejects invalid file values with the Valibot issues", async () => {
        writeFileSync(configFile, "[server]\nport = 8080.5\n");

        await expect(
            createConfig({ schema, configFile }),
        ).rejects.toBeInstanceOf(v.ValiError);
    });

    it("validates updates atomically", async () => {
        const config = await createConfig({ schema, configFile });

        await config.$set("featureEnabled", true);
        expect(config.featureEnabled).toBe(true);

        await expect(
            config.$set("server", {
                host: "api.internal",
                port: 8080.5,
            }),
        ).rejects.toBeInstanceOf(v.ValiError);
        expect(config.server).toEqual({
            host: "localhost",
            port: 3000,
        });
    });

    it("writes only validated values and can reload external changes", async () => {
        const config = await createConfig({ schema, configFile });
        await config.$set({
            featureEnabled: true,
            server: { host: "api.internal", port: 8080 },
        });

        expect(existsSync(configFile)).toBe(false);
        await config.$write();
        expect(readFileSync(configFile, "utf8")).toContain(
            'host = "api.internal"',
        );

        writeFileSync(
            configFile,
            'featureEnabled = false\n\n[server]\nhost = "new.host"\nport = 9000\n',
        );
        await config.$reload();

        expect(config.featureEnabled).toBe(false);
        expect(config.server).toEqual({ host: "new.host", port: 9000 });

        await config.$set("featureEnabled", true, { write: true });
        expect(readFileSync(configFile, "utf8")).toContain(
            "featureEnabled = true",
        );
    });

    it("prevents direct and nested mutation", async () => {
        const config = await createConfig({ schema, configFile });

        expect(() => {
            // @ts-expect-error Direct writes intentionally bypass the API.
            config.featureEnabled = true;
        }).toThrow(TypeError);
        expect(() => {
            // @ts-expect-error Nested config values are deeply read-only.
            config.server.port = 8080;
        }).toThrow(TypeError);
        expect(config.$snapshot()).toEqual({
            server: { host: "localhost", port: 3000 },
            featureEnabled: false,
        });
    });

    it("exposes only config values during enumeration", async () => {
        const config = await createConfig({ schema, configFile });

        expect(Object.keys(config)).toEqual(["server", "featureEnabled"]);
        expect(Object.entries(config)).toEqual([
            ["server", { host: "localhost", port: 3000 }],
            ["featureEnabled", false],
        ]);
    });
});
