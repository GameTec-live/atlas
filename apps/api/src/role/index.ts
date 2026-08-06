import { and, count, DrizzleQueryError, eq, sql } from "drizzle-orm";
import { Elysia, status } from "elysia";
import { DatabaseError } from "pg";
import { authHandler } from "../authHandler";
import { config } from "../config";
import { db } from "../db";
import { role, user } from "../db/schema";
import { RoleModel } from "./model";

const dispatcherLockNamespace = 0x524f4c45; // ASCII "ROLE"

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
                    name: user.name,
                })
                .from(role)
                .innerJoin(user, eq(role.driverId, user.id))
                .where(eq(role.date, sql`current_date`));

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
        async ({ body, user }) => {
            try {
                if (body.role === "dispatcher") {
                    const assignmentDate = body.date ?? sql`current_date`;
                    const claimed = await db.transaction(async (tx) => {
                        await tx.execute(
                            sql`select pg_advisory_xact_lock(
                                ${dispatcherLockNamespace},
                                ${assignmentDate}::date - date '2000-01-01'
                            )`,
                        );

                        const [numOfDispatchers] = await tx
                            .select({
                                count: count(),
                            })
                            .from(role)
                            .where(
                                and(
                                    eq(role.role, "dispatcher"),
                                    eq(role.date, assignmentDate),
                                ),
                            );

                        if (
                            (numOfDispatchers?.count ?? 0) >=
                            config.dispatchers.max
                        ) {
                            return false;
                        }

                        await tx.insert(role).values({
                            ...body,
                            driverId: user.id,
                        });
                        return true;
                    });

                    if (claimed) {
                        return { message: "Role claimed successfully" };
                    }

                    return status(418, {
                        error: "Max number of dispatchers reached",
                    });
                }

                await db.insert(role).values({
                    ...body,
                    driverId: user.id,
                });
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
