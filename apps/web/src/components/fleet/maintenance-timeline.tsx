import { WrenchIcon } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { formatDateTime } from "@/lib/date";
import { formatOdometer } from "@/lib/fleet";
import { m } from "@/paraglide/messages";
import { getLocale } from "@/paraglide/runtime";
import type { MaintenanceRecord } from "@/queries/fleet";

export function MaintenanceTimeline({
    history,
}: {
    history: MaintenanceRecord[];
}) {
    const locale = getLocale();

    if (history.length === 0) {
        return (
            <div className="rounded-xl border border-dashed p-8 text-center text-sm text-muted-foreground">
                {m.fleet_no_maintenance_history()}
            </div>
        );
    }

    return (
        <ol className="relative ml-4 border-l">
            {history.map((record, index) => (
                <li
                    key={record.id}
                    className={
                        index === history.length - 1
                            ? "relative ml-6"
                            : "relative mb-5 ml-6"
                    }
                >
                    <span className="absolute top-0 -left-9 flex size-6 items-center justify-center rounded-full border bg-background text-muted-foreground">
                        <WrenchIcon className="size-3" />
                    </span>
                    <Card size="sm">
                        <CardHeader>
                            <CardTitle>
                                {formatDateTime(record.createdAt, locale)}
                            </CardTitle>
                        </CardHeader>
                        <CardContent className="space-y-3">
                            <dl className="grid gap-1.5 text-sm sm:grid-cols-2">
                                <div>
                                    <dt className="text-xs text-muted-foreground">
                                        {m.fleet_odometer()}
                                    </dt>
                                    <dd className="font-medium">
                                        {formatOdometer(
                                            record.odometer,
                                            locale,
                                        )}
                                    </dd>
                                </div>
                                <div>
                                    <dt className="text-xs text-muted-foreground">
                                        {m.fleet_mechanic()}
                                    </dt>
                                    <dd className="font-medium">
                                        {record.mechanic ??
                                            m.fleet_not_recorded()}
                                    </dd>
                                </div>
                            </dl>
                            {record.note && (
                                <p className="border-t pt-3 text-sm whitespace-pre-wrap">
                                    {record.note}
                                </p>
                            )}
                        </CardContent>
                    </Card>
                </li>
            ))}
        </ol>
    );
}
