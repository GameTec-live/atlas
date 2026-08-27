import { QueryErrorResetBoundary } from "@tanstack/react-query";
import { CatchBoundary } from "@tanstack/react-router";
import { type ReactNode, Suspense } from "react";
import { Button } from "@/components/ui/button";
import { CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { m } from "@/paraglide/messages";

export function DashboardCardBoundary({ children }: { children: ReactNode }) {
    return (
        <QueryErrorResetBoundary>
            {({ reset: resetQuery }) => (
                <CatchBoundary
                    getResetKey={() => "dashboard-card"}
                    errorComponent={({ reset: resetBoundary }) => (
                        <CardContent className="flex min-h-0 flex-1 items-center justify-center">
                            <div
                                role="alert"
                                className="flex max-w-sm flex-col items-center gap-3 text-center"
                            >
                                <p className="text-sm text-destructive">
                                    {m.dashboard_card_error()}
                                </p>
                                <Button
                                    variant="outline"
                                    size="sm"
                                    onClick={() => {
                                        resetQuery();
                                        resetBoundary();
                                    }}
                                >
                                    {m.mushy_salty_kitten_stab()}
                                </Button>
                            </div>
                        </CardContent>
                    )}
                >
                    <Suspense
                        fallback={
                            <CardContent className="flex min-h-0 flex-1">
                                <Skeleton className="min-h-44 w-full flex-1" />
                            </CardContent>
                        }
                    >
                        {children}
                    </Suspense>
                </CatchBoundary>
            )}
        </QueryErrorResetBoundary>
    );
}
