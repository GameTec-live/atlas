import { Elysia } from "elysia";
import { db } from "./db";
import { vehicle } from "./db/schema";

const app = new Elysia()
    .get("/", async () => {
        const data = await db.select().from(vehicle);
        return data;
    })
    .listen(3000);

console.log(
    `🦊 Elysia is running at ${app.server?.hostname}:${app.server?.port}`,
);
