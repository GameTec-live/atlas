import { useSuspenseQuery } from "@tanstack/react-query";
import { UserRoundIcon } from "lucide-react";
import { Suspense, useMemo } from "react";
import { DashboardCardHeader } from "@/components/dashboard/dashboard-card-header";
import { DriverStatusItem } from "@/components/dashboard/driver-status-item";
import { Card, CardContent } from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Skeleton } from "@/components/ui/skeleton";
import { useLiveDrivers } from "@/hooks/use-live-drivers";
import { getCurrentJob } from "@/lib/jobs";
import { m } from "@/paraglide/messages";
import { getLocale } from "@/paraglide/runtime";
import { jobsQueryOptions } from "@/queries/jobs";

const cardClassName = "min-h-80 xl:col-span-3 xl:min-h-0";

export function DriversCard() {
    return (
        <Suspense
            fallback={
                <Skeleton className={`h-80 xl:h-auto ${cardClassName}`} />
            }
        >
            <DriversCardContent />
        </Suspense>
    );
}

function DriversCardContent() {
    const { data: jobs } = useSuspenseQuery(jobsQueryOptions());
    const drivers = useLiveDrivers();
    const locale = getLocale();
    const activeDrivers = useMemo(
        () =>
            [...drivers.values()].sort((left, right) =>
                left.userName.localeCompare(right.userName, locale),
            ),
        [drivers, locale],
    );

    return (
        <Card className={cardClassName}>
            <DashboardCardHeader
                title={m.dashboard_drivers_title()}
                to="/realtime"
                icon={<UserRoundIcon />}
            />
            <CardContent className="min-h-0 flex-1">
                {activeDrivers.length === 0 ? (
                    <div className="flex h-full min-h-44 items-center justify-center rounded-lg border border-dashed p-6 text-center text-sm text-muted-foreground">
                        {m.dashboard_drivers_empty()}
                    </div>
                ) : (
                    <ScrollArea className="h-full max-h-64 pr-3 xl:max-h-none">
                        <div className="space-y-2">
                            {activeDrivers.map((driver) => (
                                <DriverStatusItem
                                    key={driver.userId}
                                    driver={driver}
                                    job={getCurrentJob(jobs, driver.userId)}
                                />
                            ))}
                        </div>
                    </ScrollArea>
                )}
            </CardContent>
        </Card>
    );
}
