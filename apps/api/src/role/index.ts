import { and, count, DrizzleQueryError, eq } from "drizzle-orm";
import { Elysia, status } from "elysia";
import { DatabaseError } from "pg";
import { authHandler } from "../authHandler";
import { config } from "../config";
import { db } from "../db";
import { role } from "../db/schema";
import { RoleModel } from "./model";

export const roles = new Elysia({
    prefix: "/roles",
    tags: ["roles"],
})
    .use(authHandler)
    .get(
        "/",
        async () => {
            const roles = await db
                .select({
                    driverId: role.driverId,
                    role: role.role,
                })
                .from(role)
                .where(eq(role.date, new Date()));

            const numOfDispatchers = roles.filter(
                (r) => r.role === "dispatcher",
            ).length;

            return {
                roles,
                count: roles.length,
                dispatchers: numOfDispatchers,
                maxDispatchers: config.dispatchers.max,
                numFree: config.dispatchers.max - numOfDispatchers,
                free: config.dispatchers.max - numOfDispatchers > 0,
            };
        },
        {
            auth: true,
        },
    )
    .post(
        "/",
        async ({ body }) => {
            // Check num of dispatchers
            if (body.role === "dispatcher") {
                const numOfDispatchers = await db
                    .select({
                        count: count(),
                    })
                    .from(role)
                    .where(
                        and(
                            eq(role.role, "dispatcher"),
                            eq(role.date, body.date ?? new Date()),
                        ),
                    );
                if (
                    numOfDispatchers[0]?.count &&
                    numOfDispatchers[0].count >= config.dispatchers.max
                ) {
                    return status(418, {
                        error: "Max number of dispatchers reached",
                    });
                }
            }

            try {
                await db.insert(role).values(body);
            } catch (e) {
                if (e instanceof DrizzleQueryError) {
                    if (e.cause instanceof DatabaseError) {
                        switch (e.cause.code) {
                            case "23505":
                                return status(409, {
                                    error: "Role already claimed",
                                });
                            case "23503":
                                return status(400, {
                                    error: "Driver does not exist",
                                });
                        }
                    }
                }
                throw e;
            }

            return { message: "Role claimed successfully" };
        },
        {
            body: RoleModel.roleClaimBody,
            auth: true,
        },
    );
