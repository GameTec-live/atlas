import { createFileRoute } from "@tanstack/react-router";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { authClient } from "@/lib/auth-client";
import { m } from "@/paraglide/messages";

export const Route = createFileRoute("/_authenticated/_app/")({
    component: Dashboard,
});

function Dashboard() {
    const {
        data: session,
        isPending: isSessionPending,
        error: sessionError,
    } = authClient.useSession();
    return (
        <main>
            <h1>Dashboard</h1>
            <div>
                {isSessionPending && <p>Loading session…</p>}
                {sessionError && (
                    <Alert variant="destructive">
                        <AlertDescription>
                            Unable to load session information.
                        </AlertDescription>
                    </Alert>
                )}
                {session && (
                    <p>
                        {m.example_message({
                            username: session.user.name,
                        })}
                    </p>
                )}
            </div>
        </main>
    );
}
