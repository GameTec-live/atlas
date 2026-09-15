import { Elysia } from "elysia";
import document from "../licenses.json";

// A static import also embeds the notices in Bun's standalone executable.
export const licenses = new Elysia().get("/licenses", () => document, {
    detail: {
        summary: "Third-party licenses and project credits",
        tags: ["Licenses"],
    },
});
