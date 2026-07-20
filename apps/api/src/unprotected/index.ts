import { Elysia } from "elysia";
import { db } from "../db";
import { vehicle } from "../db/schema";

export const unauthed = new Elysia({
    prefix: "/unprotected",
    tags: ["unprotected"],
}).get("/", async () => {
    const data = await db.select().from(vehicle);
    return data;
});
