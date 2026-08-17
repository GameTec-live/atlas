import { useQuery } from "@tanstack/react-query";
import { createFileRoute, useRouter } from "@tanstack/react-router";
import { ModeToggle } from "@/components/mode-toggle";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { api, unwrapEden } from "@/lib/api-client";
import { authClient } from "@/lib/auth-client";
import { queryClient } from "@/lib/query-client";
import { m } from "@/paraglide/messages";
import { getLocale, setLocale } from "@/paraglide/runtime";

export const Route = createFileRoute("/_authenticated/")({
    component: Dashboard,
});

function Dashboard() {
    const {
        data: session,
        isPending: isSessionPending,
        error: sessionError,
    } = authClient.useSession();
    const { isPending, isError, data } = useQuery({
        queryKey: ["api", "info"],
        queryFn: () => unwrapEden(api.get()),
    });
    const router = useRouter();

    return (
        <main>
            <h1>Dashboard</h1>
            <div>
                <p>{getLocale()}</p>
                <Button onClick={() => setLocale("en")}>EN</Button>
                <Button onClick={() => setLocale("de")}>DE</Button>
            </div>
            <ModeToggle />
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
                    <>
                        <p>
                            {m.example_message({
                                username: session.user.name,
                            })}
                        </p>
                        <Button
                            onClick={async () => {
                                await authClient.signOut();
                                queryClient.clear();
                                await router.invalidate();
                            }}
                        >
                            Sign out
                        </Button>
                    </>
                )}
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
                        <dd>
                            {data.build.time?.toLocaleString() ?? "unknown"}
                        </dd>
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
