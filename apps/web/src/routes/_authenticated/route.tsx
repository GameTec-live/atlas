import {
    createFileRoute,
    type ErrorComponentProps,
    Outlet,
    redirect,
} from "@tanstack/react-router";
import {
    Alert,
    AlertAction,
    AlertDescription,
    AlertTitle,
} from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { hasAdminRole } from "@/lib/auth";
import { authClient } from "@/lib/auth-client";
import { m } from "@/paraglide/messages";

export const Route = createFileRoute("/_authenticated")({
    beforeLoad: async ({ location }) => {
        const { data: session, error } = await authClient.getSession();

        if (error) throw error;

        if (!session || !hasAdminRole(session.user.role)) {
            if (session) {
                try {
                    await authClient.signOut();
                } catch {
                    // A failed sign-out must not prevent the authorization redirect.
                }
            }

            throw redirect({
                to: "/login",
                search: {
                    redirect: location.href,
                    reason: session ? "admin-required" : undefined,
                },
                replace: true,
            });
        }

        return { session };
    },
    errorComponent: RouteError,
    component: Outlet,
});

function RouteError({ error, reset }: ErrorComponentProps) {
    return (
        <main className="mx-auto flex min-h-svh max-w-md items-center p-6">
            <Alert variant="destructive">
                <AlertTitle>{m.application_error()}</AlertTitle>
                <AlertDescription>{error.message}</AlertDescription>
                <AlertAction>
                    <Button variant="outline" onClick={reset}>
                        {m.mushy_salty_kitten_stab()}
                    </Button>
                </AlertAction>
            </Alert>
        </main>
    );
}
