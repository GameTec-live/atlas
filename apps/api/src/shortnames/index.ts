import { asc, eq } from "drizzle-orm";
import { Elysia, status } from "elysia";
import { authHandler } from "../authHandler";
import { db } from "../db";
import { shortname } from "../db/schema";
import { ShortnameModel } from "./model";

export const shortnames = new Elysia({
    prefix: "/shortnames",
    tags: ["shortnames"],
})
    .use(authHandler)
    .get("/", () => db.select().from(shortname).orderBy(asc(shortname.key)), {
        admin: true,
    })
    .get(
        "/:key",
        async ({ params }) => {
            const [foundShortname] = await db
                .select()
                .from(shortname)
                .where(eq(shortname.key, params.key.trim().toLowerCase()))
                .limit(1);

            if (!foundShortname) {
                return status(404, { error: "Shortname not found" });
            }

            return foundShortname;
        },
        {
            params: ShortnameModel.params,
            admin: true,
        },
    )
    .post(
        "/",
        async ({ body }) => {
            const [newShortname] = await db
                .insert(shortname)
                .values({
                    ...body,
                    key: body.key.trim().toLowerCase(),
                })
                .onConflictDoNothing({ target: shortname.key })
                .returning();

            if (!newShortname) {
                return status(409, { error: "Shortname already exists" });
            }

            return newShortname;
        },
        {
            body: ShortnameModel.insert,
            admin: true,
        },
    )
    .put(
        "/:key",
        async ({ params, body }) => {
            const [updatedShortname] = await db
                .update(shortname)
                .set(body)
                .where(eq(shortname.key, params.key.trim().toLowerCase()))
                .returning();

            if (!updatedShortname) {
                return status(404, { error: "Shortname not found" });
            }

            return updatedShortname;
        },
        {
            params: ShortnameModel.params,
            body: ShortnameModel.update,
            admin: true,
        },
    )
    .delete(
        "/:key",
        async ({ params }) => {
            const [deletedShortname] = await db
                .delete(shortname)
                .where(eq(shortname.key, params.key.trim().toLowerCase()))
                .returning();

            if (!deletedShortname) {
                return status(404, { error: "Shortname not found" });
            }

            return deletedShortname;
        },
        {
            params: ShortnameModel.params,
            admin: true,
        },
    );
