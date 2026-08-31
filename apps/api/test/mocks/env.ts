import { mock } from "bun:test";

type RuntimeEnv = typeof import("@/env").env;
type TestEnv = {
    -readonly [Key in keyof RuntimeEnv]: Key extends "JOBTOKEN"
        ? RuntimeEnv[Key] | undefined
        : RuntimeEnv[Key];
};

// Bun module mocks are shared across test files, so every suite uses one
// complete environment object instead of installing competing partial mocks.
export const envMock: TestEnv = {
    BETTER_AUTH_SECRET: "test-secret-that-is-at-least-32-characters",
    BETTER_AUTH_URL: "http://auth.test",
    CONFIG_FILE: undefined,
    DATA_STORAGE_PATH: undefined,
    DATABASE_URL: "postgresql://test:test@database.test/test",
    GEODATA_URL: "http://geodata.test",
    GEOCODER_URL: "http://geocoder.test",
    ROUTER_URL: "http://router.test",
    JOBTOKEN: undefined,
};

mock.module("@/env", () => ({ env: envMock }));
