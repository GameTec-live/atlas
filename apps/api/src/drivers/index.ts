import { and, asc, eq, sql } from "drizzle-orm";
import { Elysia } from "elysia";
import { authHandler } from "../authHandler";
import { db } from "../db";
import { role, user } from "../db/schema";

export const drivers = new Elysia({
    prefix: "/drivers",
    tags: ["drivers"],
})
    .use(authHandler)
    .get(
        "/",
        () =>
            db
                .select({
                    driverId: user.id,
                    name: user.name,
                    signedOn: sql<boolean>`${role.driverId} is not null`,
                })
                .from(user)
                .leftJoin(
                    role,
                    and(
                        eq(role.driverId, user.id),
                        eq(role.date, sql`current_date`),
                    ),
                )
                .orderBy(asc(user.name), asc(user.id)),
        {
            auth: true,
            detail: {
                summary: "List all drivers with today's role claim status",
            },
        },
    );
