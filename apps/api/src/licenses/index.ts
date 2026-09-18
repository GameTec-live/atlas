import { Elysia } from "elysia";
import * as v from "valibot";
import archive from "./licenses.json.gz" with { type: "file" };

const entries = v.array(
    v.object({
        name: v.string(),
        version: v.nullable(v.string()),
        creator: v.nullable(v.string()),
        license: v.nullable(v.string()),
        link: v.string(),
        source: v.array(v.string()),
        notices: v.array(v.string()),
        packages: v.optional(v.array(v.string())),
    }),
);

// Decompress and validate once at startup. Bun embeds the compressed file in
// standalone executables, so no external archive is needed at runtime.
export const licenseDocument = v.parse(
    v.object({
        web: entries,
        api: entries,
        mobile: entries,
        os: entries,
        services: entries,
        other: entries,
        specialThanks: v.array(
            v.object({
                name: v.string(),
                creator: v.string(),
                link: v.string(),
                thanks: v.string(),
            }),
        ),
        licenses: v.record(v.string(), v.string()),
    }),
    JSON.parse(
        new TextDecoder().decode(
            Bun.gunzipSync(await Bun.file(archive).bytes()),
        ),
    ),
);

export const licenses = new Elysia().get("/licenses", () => licenseDocument, {
    detail: {
        summary: "Third-party licenses and project credits",
        tags: ["Licenses"],
    },
});
