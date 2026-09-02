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

const atlasOsSetupSteps = new Set<SetupStep>([
    "timezone",
    "connection",
    "remote-access",
]);

export const getSetupSteps = (managementAvailable: boolean): SetupStep[] =>
    managementAvailable
        ? [...setupSteps]
        : setupSteps.filter((step) => !atlasOsSetupSteps.has(step));

/** Moves direct links for unavailable steps to the next step in the wizard. */
export const resolveSetupStep = (
    requestedStep: SetupStep,
    availableSteps: readonly SetupStep[],
): SetupStep =>
    availableSteps.includes(requestedStep)
        ? requestedStep
        : (setupSteps
              .slice(setupSteps.indexOf(requestedStep) + 1)
              .find((step) => availableSteps.includes(step)) ??
          availableSteps.at(-1) ??
          "greeting");

export const parseSetupStep = (value: unknown): SetupStep =>
    setupSteps.find((step) => step === value) ?? "greeting";
