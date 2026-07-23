import { Elysia } from "elysia";
import * as v from "valibot";
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
 * Application configuration loaded from `./config.toml`.
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

export const config = createConfig({ schema: configSchema });

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
        ({ body }) => {
            config.$set(body, { write: true });
            return config.$snapshot();
        },
        {
            admin: true,
            body: v.partial(configSchema),
            response: configSchema,
        },
    );
