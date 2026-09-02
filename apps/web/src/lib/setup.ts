export const setupSteps = [
    "greeting",
    "preferences",
    "admin",
    "timezone",
    "connection",
    "remote-access",
    "general",
    "app",
    "users",
    "map",
] as const;

export type SetupStep = (typeof setupSteps)[number];
export type SetupFormStep = Exclude<SetupStep, "greeting">;

export const parseSetupStep = (value: unknown): SetupStep =>
    setupSteps.find((step) => step === value) ?? "greeting";
