import { createFileRoute } from "@tanstack/react-router";
import { SetupWizard } from "@/components/setup/setup-wizard";
import { parseSetupStep } from "@/lib/setup";

export const Route = createFileRoute("/setup")({
    validateSearch: ({ step }) => ({
        step: parseSetupStep(step),
    }),
    component: SetupWizard,
});
