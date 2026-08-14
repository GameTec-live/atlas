import { createFileRoute } from "@tanstack/react-router";
import { LoginForm } from "@/components/login-form";
import { m } from "@/paraglide/messages";

function getSafeRedirect(value: unknown) {
    return typeof value === "string" &&
        value.startsWith("/") &&
        !value.startsWith("//")
        ? value
        : "/";
}

export const Route = createFileRoute("/login")({
    validateSearch: (search) => {
        const { reason, redirect } = search;
        return {
            redirect: getSafeRedirect(redirect),
            reason: reason === "admin-required" ? reason : undefined,
        };
    },
    component: Login,
});

function Login() {
    const { reason, redirect } = Route.useSearch();

    return (
        <div className="flex min-h-svh w-full items-center justify-center p-6 md:p-10">
            <div className="w-full max-w-sm">
                <LoginForm
                    redirectTo={redirect}
                    initialError={
                        reason === "admin-required"
                            ? m.admin_access_required()
                            : undefined
                    }
                />
            </div>
        </div>
    );
}
