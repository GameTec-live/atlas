import { calculateNextMaintenanceOdometer, formatOdometer } from "@/lib/fleet";
import { m } from "@/paraglide/messages";
import { getLocale } from "@/paraglide/runtime";
import type { FleetVehicle, MaintenanceRecord } from "@/queries/fleet";

export function UpcomingMaintenance({
    vehicle,
    latestMaintenance,
}: {
    vehicle: FleetVehicle;
    latestMaintenance: MaintenanceRecord | null;
}) {
    const locale = getLocale();
    const nextOdometer = calculateNextMaintenanceOdometer(
        latestMaintenance?.odometer ?? null,
        vehicle.maintenanceEvery,
    );
    const remaining =
        nextOdometer !== null && vehicle.odometer !== null
            ? nextOdometer - vehicle.odometer
            : null;

    return (
        <div className="flex flex-col justify-center items-center">
            <h3 className="font-heading text-base font-medium">
                {m.fleet_upcoming_maintenance()}
            </h3>
            {nextOdometer === null ? (
                <p className="text-sm text-muted-foreground">
                    {m.fleet_upcoming_maintenance_unavailable()}
                </p>
            ) : (
                <div className="flex flex-col items-center">
                    <span className="text-xl font-semibold">
                        {formatOdometer(nextOdometer, locale)}
                    </span>
                    {remaining !== null && (
                        <p
                            className={`text-xs ${remaining < 0 ? "text-destructive" : "text-muted-foreground"}`}
                        >
                            {remaining < 0
                                ? m.fleet_maintenance_overdue_by({
                                      distance: formatOdometer(
                                          Math.abs(remaining),
                                          locale,
                                      ),
                                  })
                                : m.fleet_maintenance_due_in({
                                      distance: formatOdometer(
                                          remaining,
                                          locale,
                                      ),
                                  })}
                        </p>
                    )}
                </div>
            )}
        </div>
    );
}
