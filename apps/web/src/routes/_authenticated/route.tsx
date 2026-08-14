import {
    createFileRoute,
    Outlet,
    redirect,
    useRouter,
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
            if (session) await authClient.signOut();

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
    errorComponent: AuthenticationError,
    component: Outlet,
});

function AuthenticationError() {
    const router = useRouter();

    return (
        <main className="mx-auto flex min-h-svh max-w-md items-center p-6">
            <Alert variant="destructive">
                <AlertTitle>{m.bland_novel_bee_scoop()}</AlertTitle>
                <AlertDescription>{m.teal_few_nils_cry()}</AlertDescription>
                <AlertAction>
                    <Button
                        variant="outline"
                        onClick={() => void router.invalidate()}
                    >
                        {m.mushy_salty_kitten_stab()}
                    </Button>
                </AlertAction>
            </Alert>
        </main>
    );
}
