import { LoginForm } from "@/components/login-form";
import { AdminAccountForm } from "@/components/setup/admin-account-form";
import { m } from "@/paraglide/messages";

export function AdminStep({
    adminRequired,
    hasSession,
    onComplete,
}: {
    adminRequired: boolean;
    hasSession: boolean;
    onComplete: () => Promise<void>;
}) {
    if (!adminRequired && !hasSession) {
        return (
            <div className="mx-auto max-w-sm text-center">
                <h2 className="text-lg font-medium">
                    {m.setup_resume_title()}
                </h2>
                <p className="mt-1 mb-6 text-sm text-muted-foreground">
                    {m.setup_resume_description()}
                </p>
                <LoginForm redirectTo="/setup?step=timezone" />
            </div>
        );
    }

    return (
        <AdminAccountForm isComplete={!adminRequired} onComplete={onComplete} />
    );
}
