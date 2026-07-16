import type { ElysiaOpenAPIConfig } from "@elysia/openapi";
import { betterAuth } from "better-auth";
import { drizzleAdapter } from "better-auth/adapters/drizzle";
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

// ----------------- OpenAPI black magic -----------------

let _schema: ReturnType<typeof auth.api.generateOpenAPISchema>;
const getSchema = async () => (_schema ??= auth.api.generateOpenAPISchema());

type OpenAPIDocumentation = NonNullable<ElysiaOpenAPIConfig["documentation"]>;
type OpenAPIComponents = NonNullable<OpenAPIDocumentation["components"]>;
type OpenAPIPaths = NonNullable<OpenAPIDocumentation["paths"]>;
type OpenAPIIntegration = {
    getPaths: (prefix?: string) => Promise<OpenAPIPaths>;
    components: Promise<OpenAPIComponents>;
};

const getPaths = async (prefix = "/api/auth"): Promise<OpenAPIPaths> => {
    const { paths } = await getSchema();
    const reference: typeof paths = Object.create(null);

    for (const path of Object.keys(paths)) {
        const pathDefinition = paths[path];
        if (!pathDefinition) continue;

        const key = prefix + path;
        reference[key] = pathDefinition;
        for (const operation of Object.values(pathDefinition)) {
            operation.tags = ["Better Auth"];
        }
    }

    // better-auth emits OpenAPI 3.1-compatible objects, but its declarations are
    // looser than the OpenAPI types consumed by Elysia.
    return reference as unknown as OpenAPIPaths;
};

const getComponents = async (): Promise<OpenAPIComponents> => {
    const { components } = await getSchema();
    return components as unknown as OpenAPIComponents;
};

export const OpenAPI: OpenAPIIntegration = {
    getPaths,
    components: getComponents(),
};
