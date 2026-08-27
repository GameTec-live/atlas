import { Link } from "@tanstack/react-router";
import { ExternalLinkIcon } from "lucide-react";
import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { CardAction, CardHeader, CardTitle } from "@/components/ui/card";
import { m } from "@/paraglide/messages";

type DashboardDestination = "/realtime" | "/fleet" | "/jobs" | "/logbook";

type DashboardViewTransition = { types: string[] };

export const dashboardViewTransitions = {
    drivers: { types: ["dashboard-drivers"] },
    maintenance: { types: ["dashboard-maintenance"] },
    map: { types: ["dashboard-map"] },
    distance: { types: ["dashboard-distance"] },
    jobs: { types: ["dashboard-jobs"] },
} satisfies Record<string, DashboardViewTransition>;

export type DashboardTransition = keyof typeof dashboardViewTransitions;

export function DashboardCardHeader({
    title,
    to,
    icon,
    transition,
}: {
    title: string;
    to: DashboardDestination;
    icon: ReactNode;
    transition: DashboardTransition;
}) {
    const linkLabel = m.dashboard_open_section({ section: title });

    return (
        <CardHeader>
            <CardTitle className="flex items-center gap-2">
                <span className="flex size-8 items-center justify-center rounded-lg bg-muted text-muted-foreground [&>svg]:size-4">
                    {icon}
                </span>
                {title}
            </CardTitle>
            <CardAction>
                <Button
                    variant="ghost"
                    size="icon"
                    nativeButton={false}
                    render={
                        <Link
                            to={to}
                            viewTransition={
                                dashboardViewTransitions[transition]
                            }
                        />
                    }
                    aria-label={linkLabel}
                    title={linkLabel}
                >
                    <ExternalLinkIcon />
                </Button>
            </CardAction>
        </CardHeader>
    );
}
