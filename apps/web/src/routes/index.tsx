import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { api, unwrapEden } from "@/lib/api-client";
import { authClient } from "@/lib/auth-client";

export const Route = createFileRoute("/")({ component: Home });

function Home() {
    const {
        data: session,
        isPending: isSessionPending,
        error: sessionError,
    } = authClient.useSession();
    const { isPending, isError, data } = useQuery({
        queryKey: ["api", "info"],
        queryFn: () => unwrapEden(api.get()),
    });

    return (
        <main>
            <div>
                {isSessionPending && <p>Loading session…</p>}
                {sessionError && (
                    <Alert variant="destructive">
                        <AlertDescription>
                            Unable to load session information.
                        </AlertDescription>
                    </Alert>
                )}
                {session && <p>Signed in as {session.user.email}</p>}
            </div>

            <div>
                {isPending && <p>Loading…</p>}
                {isError && (
                    <Alert variant="destructive">
                        <AlertDescription>
                            Unable to load API information.
                        </AlertDescription>
                    </Alert>
                )}
                {data && (
                    <dl>
                        <dt className="text-muted-foreground">Version</dt>
                        <dd>{data.build.version}</dd>
                        <dt className="text-muted-foreground">Built</dt>
                        <dd>{data.build.time}</dd>
                        <dt className="text-muted-foreground">Commit</dt>
                        <dd className="truncate font-mono text-xs">
                            {data.build.commit}
                        </dd>
                    </dl>
                )}
            </div>
        </main>
    );
}
