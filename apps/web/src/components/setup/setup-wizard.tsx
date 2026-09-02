import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
    Navigate,
    useNavigate,
    useRouter,
    useSearch,
} from "@tanstack/react-router";
import { GeneralSettingsCard } from "@/components/settings/general-settings-card";
import { MapDataCard } from "@/components/settings/map-data-card";
import { UsersCard } from "@/components/settings/users-card";
import { AdminStep } from "@/components/setup/admin-step";
import { AppConnectionStep } from "@/components/setup/app-connection-step";
import { ConnectionStep } from "@/components/setup/connection-step";
import { GreetingStep } from "@/components/setup/greeting-step";
import { PreferencesStep } from "@/components/setup/preferences-step";
import { RemoteAccessStep } from "@/components/setup/remote-access-step";
import { SetupStepLayout } from "@/components/setup/setup-step-layout";
import { TimezoneStep } from "@/components/setup/timezone-step";
import { WizardProgress } from "@/components/setup/wizard-progress";
import { Spinner } from "@/components/ui/spinner";
import { authClient } from "@/lib/auth-client";
import {
    getSetupSteps,
    resolveSetupStep,
    type SetupStep,
    setupSteps,
} from "@/lib/setup";
import {
    completeSetupMutationOptions,
    setupStatusQueryKey,
    setupStatusQueryOptions,
} from "@/queries/setup";

export function SetupWizard() {
    const router = useRouter();
    const navigate = useNavigate({ from: "/setup" });
    const { step: requestedStep } = useSearch({ from: "/setup" });
    const queryClient = useQueryClient();
    const setupQuery = useQuery(setupStatusQueryOptions());
    const sessionQuery = authClient.useSession();
    const completeMutation = useMutation({
        ...completeSetupMutationOptions(),
        onSuccess: async () => {
            await queryClient.invalidateQueries({
                queryKey: setupStatusQueryKey,
            });
            await router.navigate({ to: "/", replace: true });
        },
    });
    const requestedIndex = setupSteps.indexOf(requestedStep);
    const isWaitingForSession =
        setupQuery.data?.adminRequired === false && sessionQuery.isPending;
    const hasSetupAccess =
        setupQuery.data?.adminRequired === false && Boolean(sessionQuery.data);
    const availableSteps = getSetupSteps(
        setupQuery.data?.capabilities.systemManagement === true,
    );
    const accessStep =
        requestedIndex > setupSteps.indexOf("admin") &&
        !setupQuery.isPending &&
        !isWaitingForSession &&
        !hasSetupAccess
            ? "admin"
            : requestedStep;
    const step = resolveSetupStep(accessStep, availableSteps);
    const stepIndex = availableSteps.indexOf(step);
    const goTo = (nextStep: SetupStep) =>
        navigate({ search: { step: nextStep } });

    if (setupQuery.isPending || isWaitingForSession) {
        return (
            <main className="grid min-h-svh place-items-center bg-muted/30">
                <Spinner className="size-6" />
            </main>
        );
    }

    if (step !== requestedStep) {
        return <Navigate to="/setup" search={{ step }} replace />;
    }

    const previousStep = availableSteps[Math.max(0, stepIndex - 1)];
    const nextStep = availableSteps[stepIndex + 1];

    return (
        <main className="relative flex min-h-svh flex-col overflow-hidden bg-muted/35">
            <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,var(--color-background),transparent_42%)] opacity-80" />
            {step === "greeting" ? (
                <GreetingStep onStart={() => void goTo("preferences")} />
            ) : (
                <SetupStepLayout
                    step={step}
                    steps={availableSteps}
                    canContinue={step !== "admin" || hasSetupAccess}
                    isFinishing={completeMutation.isPending}
                    onPrevious={() => previousStep && void goTo(previousStep)}
                    onNext={() => nextStep && void goTo(nextStep)}
                    onFinish={() => completeMutation.mutate()}
                >
                    {step === "preferences" && <PreferencesStep />}
                    {step === "admin" && (
                        <AdminStep
                            adminRequired={
                                setupQuery.data?.adminRequired ?? true
                            }
                            hasSession={Boolean(sessionQuery.data)}
                            onComplete={async () => {
                                await sessionQuery.refetch();
                                await queryClient.invalidateQueries({
                                    queryKey: setupStatusQueryKey,
                                });
                                await goTo(
                                    resolveSetupStep(
                                        "timezone",
                                        availableSteps,
                                    ),
                                );
                            }}
                        />
                    )}
                    {step === "timezone" && <TimezoneStep />}
                    {step === "connection" && <ConnectionStep />}
                    {step === "remote-access" && <RemoteAccessStep />}
                    {step === "general" && <GeneralSettingsCard />}
                    {step === "app" && <AppConnectionStep />}
                    {step === "users" && <UsersCard />}
                    {step === "map" && <MapDataCard />}
                </SetupStepLayout>
            )}
            <WizardProgress
                stepIndex={stepIndex}
                totalSteps={availableSteps.length}
            />
        </main>
    );
}
