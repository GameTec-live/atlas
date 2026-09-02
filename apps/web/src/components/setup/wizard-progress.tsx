import { m } from "@/paraglide/messages";

export function WizardProgress({
    stepIndex,
    totalSteps,
}: {
    stepIndex: number;
    totalSteps: number;
}) {
    const currentStep = stepIndex + 1;

    return (
        <div
            className="absolute inset-x-0 bottom-0 z-30 h-2 bg-muted"
            role="progressbar"
            aria-valuemin={1}
            aria-valuemax={totalSteps}
            aria-valuenow={currentStep}
            aria-label={m.setup_step_progress({
                current: currentStep,
                total: totalSteps,
            })}
        >
            <div
                className={`h-full bg-primary transition-all duration-500 ease-out ${currentStep === totalSteps ? "" : "rounded-r-full"}`}
                style={{ width: `${(currentStep / totalSteps) * 100}%` }}
            />
        </div>
    );
}
