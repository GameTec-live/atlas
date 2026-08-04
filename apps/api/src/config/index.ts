import { Elysia, file, status } from "elysia";
import * as v from "valibot";
import { env } from "@/env";
import { authHandler } from "../authHandler";
import {
    findLogoFile,
    isLogoContentType,
    LogoLockError,
    LogoTooLargeError,
    logoExtensions,
    readLogoBody,
    replaceLogo,
} from "./logo";
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
 *
 * [dispatchers]
 * max = 2
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
    dispatchers: v.optional(
        v.object({
            max: v.optional(v.number(), 1),
        }),
        { max: 1 },
    ),
    storage: v.optional(
        v.object({
            dataLocation: v.optional(
                v.string(),
                env.DATA_STORAGE_PATH ?? "./data",
            ),
            logoName: v.optional(v.string(), "logo"),
        }),
        { dataLocation: env.DATA_STORAGE_PATH ?? "./data", logoName: "logo" },
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
    )
    .get("/logo", async () => {
        const logoFilePath = await findLogoFile(config.storage);

        return logoFilePath
            ? file(logoFilePath)
            : status(404, "Logo file not found");
    })
    .put(
        "/logo",
        async ({ request }) => {
            const contentType = request.headers
                .get("content-type")
                ?.split(";", 1)[0]
                ?.trim()
                .toLowerCase();

            if (!contentType || !isLogoContentType(contentType)) {
                return status(415, "Unsupported logo image type");
            }

            let logo: Blob;
            try {
                logo = await readLogoBody(request, contentType);
            } catch (error) {
                if (error instanceof LogoTooLargeError) {
                    return status(413, error.message);
                }
                throw error;
            }

            if (logo.size === 0) {
                return status(400, "Logo file is empty");
            }

            const extension = logoExtensions[contentType];
            try {
                await replaceLogo(config.storage, extension, logo);
            } catch (error) {
                if (error instanceof LogoLockError) {
                    return status(503, error.message);
                }
                throw error;
            }

            return;
        },
        {
            admin: true,
            parse: "none",
            detail: {
                requestBody: {
                    required: true,
                    content: {
                        "image/*": {
                            schema: { type: "string", format: "binary" },
                        },
                    },
                },
            },
        },
    );
