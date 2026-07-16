import type { ElysiaOpenAPIConfig } from "@elysia/openapi";
import { type Auth, type BetterAuthOptions, betterAuth } from "better-auth";
import { drizzleAdapter } from "better-auth/adapters/drizzle";
import {
    type AdminOptions,
    admin,
    type OpenAPIOptions,
    openAPI,
    username,
} from "better-auth/plugins";
import { db } from "./db";
import * as schema from "./db/schema";

type AtlasAuthOptions = Omit<BetterAuthOptions, "plugins"> & {
    // fixes error TS2883: The inferred type of 'auth' cannot be named without a reference to '$strip'
    plugins: [
        ReturnType<typeof openAPI<OpenAPIOptions>>,
        ReturnType<typeof username>,
        ReturnType<typeof admin<AdminOptions>>,
    ];
};

export const auth: Auth<AtlasAuthOptions> = betterAuth<AtlasAuthOptions>({
    database: drizzleAdapter(db, {
        provider: "pg",
        schema,
    }),
    plugins: [openAPI(), username(), admin()],
    emailAndPassword: {
        enabled: true,
    },
});

// ----------------- OpenAPI black magic -----------------

type AuthField = {
    type: string;
    required?: boolean;
    input?: boolean;
    returned?: boolean;
    defaultValue?: unknown;
};

type JSONSchema = {
    type?: string | string[];
    format?: string;
    default?: unknown;
    properties?: Record<string, JSONSchema> & {
        user?: JSONSchema;
    };
    required?: string[];
};

type MutableOpenAPISchema = {
    paths?: Record<
        string,
        {
            post?: {
                requestBody?: {
                    content?: Record<string, { schema?: JSONSchema }>;
                };
                responses?: Record<
                    string,
                    {
                        content?: Record<string, { schema?: JSONSchema }>;
                    }
                >;
            };
        }
    >;
};

type AuthSchemaSource = {
    schema?: {
        user?: {
            fields?: Record<string, AuthField>;
        };
    };
};

const getAdditionalUserFields = (): Record<string, AuthField> => {
    const options = auth.options as {
        user?: {
            additionalFields?: Record<string, AuthField>;
        };
        plugins: AuthSchemaSource[];
    };
    const fields = {
        ...options.user?.additionalFields,
    };

    for (const plugin of options.plugins) {
        Object.assign(fields, plugin.schema?.user?.fields);
    }

    return fields;
};

const getFieldSchema = (field: AuthField): JSONSchema => ({
    type: field.type === "date" ? "string" : field.type,
    ...(field.type === "date" ? { format: "date-time" } : {}),
    ...(field.defaultValue !== undefined &&
    typeof field.defaultValue !== "function"
        ? { default: field.defaultValue }
        : {}),
});

const addAdditionalUserFields = (
    schema: JSONSchema | undefined,
    mode: "input" | "output",
) => {
    if (schema?.type !== "object") return;

    schema.properties ??= {};
    const required = new Set(schema.required);

    for (const [name, field] of Object.entries(getAdditionalUserFields())) {
        if (mode === "input" && field.input === false) continue;
        if (mode === "output" && field.returned === false) continue;

        schema.properties[name] ??= getFieldSchema(field);
        if (
            field.required &&
            (mode === "output" || field.defaultValue === undefined)
        ) {
            required.add(name);
        }
    }

    if (required.size > 0) schema.required = [...required];
};

const patchPluginFields = (document: unknown) => {
    const schema = document as MutableOpenAPISchema;
    const operation = schema.paths?.["/sign-up/email"]?.post;

    addAdditionalUserFields(
        operation?.requestBody?.content?.["application/json"]?.schema,
        "input",
    );
    addAdditionalUserFields(
        operation?.responses?.["200"]?.content?.["application/json"]?.schema
            ?.properties?.user,
        "output",
    );
};

let schemaPromise:
    | ReturnType<typeof auth.api.generateOpenAPISchema>
    | undefined;
const getSchema = async () => {
    schemaPromise ??= auth.api.generateOpenAPISchema();
    const schema = await schemaPromise;
    patchPluginFields(schema);
    return schema;
};

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
