import { setupSteps } from "@/lib/setup";
import { m } from "@/paraglide/messages";

export function WizardProgress({ stepIndex }: { stepIndex: number }) {
    const currentStep = stepIndex + 1;

    return (
        <div
            className="absolute inset-x-0 bottom-0 z-30 h-2 bg-muted"
            role="progressbar"
            aria-valuemin={1}
            aria-valuemax={setupSteps.length}
            aria-valuenow={currentStep}
            aria-label={m.setup_step_progress({
                current: currentStep,
                total: setupSteps.length,
            })}
        >
            <div
                className={`h-full bg-primary transition-all duration-500 ease-out ${currentStep === setupSteps.length ? "" : "rounded-r-full"}`}
                style={{ width: `${(currentStep / setupSteps.length) * 100}%` }}
            />
        </div>
    );
}
