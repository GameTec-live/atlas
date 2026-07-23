import { Elysia } from "elysia";
import * as v from "valibot";
import { env } from "@/env";
import { authHandler } from "../authHandler";
import { createConfig } from "./provider";

export {
    type Config,
    type ConfigControls,
    type ConfigSetOptions,
    type CreateConfigOptions,
    createConfig,
} from "./provider";

/**
 * Application configuration loaded from `CONFIG_FILE`, or `./config.toml`
 * when the environment variable is not set.
 *
 * @example
 * ```toml
 * [routing]
 * defaultLanguage = "de-AT"
 * ```
 */
export const configSchema = v.object({
    routing: v.optional(
        v.object({
            defaultLanguage: v.optional(
                v.pipe(v.string(), v.minLength(2), v.maxLength(5)),
                "en-US",
            ),
        }),
        { defaultLanguage: "en-US" },
    ),
});

export const config = await createConfig({
    schema: configSchema,
    configFile: env.CONFIG_FILE,
});

export const configApp = new Elysia({
    prefix: "/config",
    tags: ["config"],
})
    .use(authHandler)
    .get("/", () => config.$snapshot(), {
        admin: true,
        response: configSchema,
    })
    .put(
        "/",
        async ({ body }) => {
            await config.$set(body, { write: true });
            return config.$snapshot();
        },
        {
            admin: true,
            body: v.partial(configSchema),
            response: configSchema,
        },
    );
