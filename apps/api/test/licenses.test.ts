import { describe, expect, it } from "bun:test";
import { Elysia } from "elysia";
import * as v from "valibot";
import { licenseDocument as document, licenses } from "../src/licenses";

const groups = ["web", "api", "mobile", "os", "services", "other"] as const;

describe("license inventory", () => {
    it("serves the complete document without credentials", async () => {
        const app = new Elysia().use(licenses);
        const response = await app.handle(
            new Request("http://localhost/licenses"),
        );

        expect(response.status).toBe(200);
        expect(response.headers.get("content-type")).toContain(
            "application/json",
        );
        expect(await response.json()).toEqual(document);
        expect(Object.keys(document)).toEqual([
            ...groups,
            "specialThanks",
            "licenses",
        ]);
        expect(document.specialThanks.length).toBeGreaterThan(0);
    });

    it("resolves every license and notice to one unique text", () => {
        const texts = Object.values(document.licenses);
        const identifiers = new Set(Object.keys(document.licenses));
        expect(new Set(texts).size).toBe(texts.length);
        for (const text of texts) {
            expect(text.trim().length).toBeGreaterThan(0);
            expect(text).not.toContain("\0");
        }
        for (const group of groups) {
            expect(document[group].length).toBeGreaterThan(0);
            for (const project of document[group]) {
                expect(project.name.length).toBeGreaterThan(0);
                for (const notice of project.notices) {
                    expect(identifiers.has(notice)).toBe(true);
                }
                for (const identifier of project.license?.match(
                    /[A-Za-z0-9.+-]+/g,
                ) ?? []) {
                    if (["AND", "OR", "WITH"].includes(identifier)) continue;
                    expect(identifiers.has(identifier)).toBe(true);
                }
            }
        }
    });

    it("includes every npm version from both checked-in lockfiles", async () => {
        const entries = new Set(
            groups.flatMap((group) =>
                document[group].map(
                    (project) => `${project.name}@${project.version}`,
                ),
            ),
        );
        for (const path of ["../../../bun.lock", "../../../docs/bun.lock"]) {
            const lock = v.parse(
                v.object({
                    packages: v.record(
                        v.string(),
                        v.tupleWithRest([v.string()], v.unknown()),
                    ),
                }),
                Bun.JSONC.parse(
                    await Bun.file(new URL(path, import.meta.url)).text(),
                ),
            );
            for (const [specifier] of Object.values(lock.packages)) {
                if (specifier.includes("@workspace:")) continue;
                expect(entries.has(specifier)).toBe(true);
            }
        }
    });
});
