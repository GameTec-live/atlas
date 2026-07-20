import { readdir } from "node:fs/promises";
import { migrate } from "drizzle-orm/node-postgres/migrator";
import { db } from ".";

export const runMigrations = async () => {
    try {
        await readdir("./drizzle");
        await migrate(db, { migrationsFolder: "./drizzle" });
        console.log("Database migrations ran successfully.");
    } catch (error) {
        if ((error as NodeJS.ErrnoException).code === "ENOENT") {
            console.log(
                "No migrations folder found. Skipping database migrations.",
            );
        } else {
            console.error("Error running database migrations:", error);
        }
    }
};
