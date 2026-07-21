import { createEnv } from "@t3-oss/env-core";
import * as v from "valibot";

export const env = createEnv({
    server: {
        BETTER_AUTH_SECRET: v.pipe(v.string(), v.minLength(32)),
        BETTER_AUTH_URL: v.pipe(v.string(), v.url()),
        DATABASE_URL: v.pipe(v.string(), v.minLength(1)),
        GEOCODER_URL: v.pipe(v.string(), v.url()),
    },
    runtimeEnv: process.env,
    emptyStringAsUndefined: true,
});
