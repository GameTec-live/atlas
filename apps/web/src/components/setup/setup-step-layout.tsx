import { ArrowLeftIcon, ArrowRightIcon, CheckIcon } from "lucide-react";
import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { type SetupFormStep, setupSteps } from "@/lib/setup";
import { m } from "@/paraglide/messages";

const getStepCopy = (step: SetupFormStep) =>
    ({
        preferences: [
            m.setup_preferences_title(),
            m.setup_preferences_description(),
        ],
        admin: [m.setup_admin_title(), m.setup_admin_description()],
        timezone: [m.setup_timezone_title(), m.setup_timezone_description()],
        connection: [
            m.setup_connection_title(),
            m.setup_connection_description(),
        ],
        "remote-access": [
            m.setup_remote_access_title(),
            m.setup_remote_access_description(),
        ],
        general: [m.setup_general_title(), m.setup_general_description()],
        app: [m.setup_app_title(), m.setup_app_description()],
        users: [m.setup_users_title(), m.setup_users_description()],
        map: [m.setup_map_title(), m.setup_map_description()],
    })[step];

export function SetupStepLayout({
    step,
    children,
    canContinue,
    isFinishing,
    onPrevious,
    onNext,
    onFinish,
}: {
    step: SetupFormStep;
    children: ReactNode;
    canContinue: boolean;
    isFinishing: boolean;
    onPrevious: () => void;
    onNext: () => void;
    onFinish: () => void;
}) {
    const [title, description] = getStepCopy(step);
    const currentStep = setupSteps.indexOf(step) + 1;

    return (
        <>
            <header className="relative z-10 shrink-0 px-5 pt-6 sm:px-8 sm:pt-8 lg:px-12">
                <p className="mb-2 text-xs font-medium tracking-widest text-muted-foreground uppercase">
                    {m.setup_step_progress({
                        current: currentStep,
                        total: setupSteps.length,
                    })}
                </p>
                <h1 className="font-heading text-3xl font-semibold tracking-tight sm:text-4xl">
                    {title}
                </h1>
                <p className="mt-2 max-w-2xl text-sm text-muted-foreground sm:text-base">
                    {description}
                </p>
            </header>
            <section className="relative z-10 flex min-h-0 flex-1 overflow-y-auto px-5 pt-8 pb-32 sm:px-8 lg:px-12">
                <div className="m-auto w-full max-w-6xl">{children}</div>
            </section>
            <div className="absolute inset-x-0 bottom-2 z-20 flex items-center justify-between px-5 py-5 sm:px-8 lg:px-12">
                <Button
                    type="button"
                    variant="outline"
                    size="lg"
                    onClick={onPrevious}
                    disabled={isFinishing}
                >
                    <ArrowLeftIcon data-icon="inline-start" />
                    {m.setup_previous()}
                </Button>
                {step === "map" ? (
                    <Button
                        type="button"
                        size="lg"
                        onClick={onFinish}
                        disabled={isFinishing}
                    >
                        {isFinishing ? (
                            <Spinner />
                        ) : (
                            <CheckIcon data-icon="inline-start" />
                        )}
                        {m.setup_finish()}
                    </Button>
                ) : (
                    <Button
                        type="button"
                        size="lg"
                        onClick={onNext}
                        disabled={!canContinue || isFinishing}
                    >
                        {m.setup_next()}
                        <ArrowRightIcon data-icon="inline-end" />
                    </Button>
                )}
            </div>
        </>
    );
}
