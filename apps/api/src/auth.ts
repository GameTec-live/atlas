import { drizzleAdapter } from "@better-auth/drizzle-adapter/relations-v2";
import { betterAuth } from "better-auth";
import { openAPI } from "better-auth/plugins";
import { db } from "./db";
import * as schema from "./db/schema";

export const auth = betterAuth({
    database: drizzleAdapter(db, {
        provider: "pg",
        schema,
    }),
    plugins: [openAPI()],
    emailAndPassword: {
        enabled: true,
    },
});

let _schema: ReturnType<typeof auth.api.generateOpenAPISchema>;
const getSchema = async () => (_schema ??= auth.api.generateOpenAPISchema());
export const OpenAPI = {
    getPaths: (prefix = "/api/auth") =>
        getSchema().then(({ paths }) => {
            const reference: typeof paths = Object.create(null);
            for (const path of Object.keys(paths)) {
                const pathDefinition = paths[path];
                if (!pathDefinition) continue;

                const key = prefix + path;
                reference[key] = pathDefinition;
                for (const method of Object.keys(pathDefinition)) {
                    const operation = (reference[key] as any)[method];
                    operation.tags = ["Better Auth"];
                }
            }
            return reference;
        }) as Promise<any>,
    components: getSchema().then(
        ({ components }) => components,
    ) as Promise<any>,
} as const;
