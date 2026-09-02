import { useQuery } from "@tanstack/react-query";
import { ServerIcon } from "lucide-react";
import { SystemManagementCard } from "@/components/settings/system/system-management-card";
import { SystemRuntimeInfoCard } from "@/components/settings/system/system-runtime-info-card";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { m } from "@/paraglide/messages";
import { systemAvailabilityQueryOptions } from "@/queries/system";

function SystemCardSkeleton() {
    return (
        <Card className="lg:col-span-4">
            <CardHeader>
                <CardTitle className="flex items-center gap-2">
                    <ServerIcon className="size-4" />
                    {m.settings_system()}
                </CardTitle>
            </CardHeader>
            <CardContent className="grid gap-3">
                {[0, 1, 2].map((item) => (
                    <Skeleton key={item} className="h-12" />
                ))}
            </CardContent>
        </Card>
    );
}

export function SystemCard() {
    const availability = useQuery(systemAvailabilityQueryOptions());

    if (availability.isPending) return <SystemCardSkeleton />;

    return availability.data?.management.available ? (
        <SystemManagementCard />
    ) : (
        <SystemRuntimeInfoCard />
    );
}
