import { drizzle, type NodePgDatabase } from "drizzle-orm/node-postgres";
import { env } from "@/env";
import { relations } from "./schema";

export const db: NodePgDatabase<typeof relations> = drizzle(env.DATABASE_URL, {
    relations,
});
