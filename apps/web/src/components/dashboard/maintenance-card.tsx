import { useSuspenseQuery } from "@tanstack/react-query";
import { WrenchIcon } from "lucide-react";
import { DashboardCardBoundary } from "@/components/dashboard/dashboard-card-boundary";
import { DashboardCardHeader } from "@/components/dashboard/dashboard-card-header";
import { MaintenanceItem } from "@/components/dashboard/maintenance-item";
import { Card, CardContent } from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";
import { getUpcomingMaintenance } from "@/lib/fleet";
import { m } from "@/paraglide/messages";
import { getLocale } from "@/paraglide/runtime";
import { fleetQueryOptions } from "@/queries/fleet";

const cardClassName = "min-h-80 xl:col-span-3 xl:min-h-0";

export function MaintenanceCard() {
    return (
        <Card className={cardClassName}>
            <DashboardCardHeader
                title={m.dashboard_maintenance_title()}
                to="/fleet"
                icon={<WrenchIcon />}
            />
            <DashboardCardBoundary>
                <MaintenanceCardContent />
            </DashboardCardBoundary>
        </Card>
    );
}

function MaintenanceCardContent() {
    const { data: fleet } = useSuspenseQuery(fleetQueryOptions());
    const locale = getLocale();
    const upcoming = getUpcomingMaintenance(fleet);

    return (
        <CardContent className="min-h-0 flex-1">
            {upcoming.length === 0 ? (
                <div className="flex h-full min-h-44 items-center justify-center rounded-lg border border-dashed p-6 text-center text-sm text-muted-foreground">
                    {m.dashboard_maintenance_empty()}
                </div>
            ) : (
                <ScrollArea className="h-full max-h-64 pr-3 xl:max-h-none">
                    <div className="space-y-2">
                        {upcoming.map((row) => (
                            <MaintenanceItem
                                key={row.vehicle.id}
                                row={row}
                                locale={locale}
                            />
                        ))}
                    </div>
                </ScrollArea>
            )}
        </CardContent>
    );
}
