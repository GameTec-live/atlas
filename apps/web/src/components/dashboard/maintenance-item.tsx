import { Badge } from "@/components/ui/badge";
import { formatDate } from "@/lib/date";
import { formatOdometer, type UpcomingMaintenance } from "@/lib/fleet";
import { m } from "@/paraglide/messages";
import type { FleetRow } from "@/queries/fleet";

export function MaintenanceItem({
    row,
    locale,
}: {
    row: UpcomingMaintenance<FleetRow>;
    locale: string;
}) {
    const dueLabel = formatDueLabel(row.remaining, locale);

    return (
        <div className="flex items-start gap-3 rounded-lg border bg-background p-3">
            <div className="min-w-0 flex-1">
                <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                        <p className="truncate font-medium">
                            {row.vehicle.brand} {row.vehicle.model}
                        </p>
                        <p className="text-xs text-muted-foreground">
                            {row.vehicle.licensePlate}
                        </p>
                    </div>
                    {dueLabel && (
                        <Badge
                            variant={
                                row.remaining !== null && row.remaining < 0
                                    ? "destructive"
                                    : "secondary"
                            }
                        >
                            {dueLabel}
                        </Badge>
                    )}
                </div>
                <dl className="mt-2 grid grid-cols-[auto_1fr] gap-x-2 gap-y-1 text-xs">
                    <dt className="text-muted-foreground">
                        {m.fleet_last_maintenance()}
                    </dt>
                    <dd className="text-right tabular-nums">
                        {formatDate(
                            row.maintenance?.createdAt ?? null,
                            locale,
                            m.jobs_not_available(),
                        )}
                    </dd>
                    <dt className="text-muted-foreground">
                        {m.dashboard_maintenance_due_at()}
                    </dt>
                    <dd className="text-right font-medium tabular-nums">
                        {formatOdometer(row.nextOdometer, locale)}
                    </dd>
                </dl>
            </div>
        </div>
    );
}

function formatDueLabel(remaining: number | null, locale: string) {
    if (remaining === null) return null;

    const distance = formatOdometer(Math.abs(remaining), locale);
    return remaining < 0
        ? m.fleet_maintenance_overdue_by({ distance })
        : m.fleet_maintenance_due_in({ distance });
}
