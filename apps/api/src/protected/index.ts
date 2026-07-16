import { Elysia } from "elysia";
import { authHandler } from "../authHandler";
import { db } from "../db";
import { vehicle } from "../db/schema";

export const authed = new Elysia({ prefix: "/protected", tags: ["protected"] })
    .use(authHandler)
    .get(
        "/",
        async () => {
            const data = await db.select().from(vehicle);
            return data;
        },
        {
            auth: true,
        },
    );
