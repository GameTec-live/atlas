import { useQuery } from "@tanstack/react-query";
import { MaintenanceTimeline } from "@/components/fleet/maintenance-timeline";
import { UpcomingMaintenance } from "@/components/fleet/upcoming-maintenance";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Skeleton } from "@/components/ui/skeleton";
import { m } from "@/paraglide/messages";
import {
    type FleetVehicle,
    maintenanceHistoryQueryOptions,
} from "@/queries/fleet";

function HistorySkeleton() {
    return (
        <div className="space-y-4">
            <Skeleton className="h-28 w-full" />
            <Skeleton className="h-32 w-full" />
            <Skeleton className="h-32 w-full" />
        </div>
    );
}

export function MaintenanceHistoryDialog({
    vehicle,
    onClose,
}: {
    vehicle: FleetVehicle | null;
    onClose: () => void;
}) {
    const vehicleId = vehicle?.id ?? "";
    const { data, isPending, isError } = useQuery({
        ...maintenanceHistoryQueryOptions(vehicleId),
        enabled: vehicle !== null,
    });

    return (
        <Dialog
            open={vehicle !== null}
            onOpenChange={(open) => {
                if (!open) onClose();
            }}
        >
            <DialogContent className="grid max-h-[calc(100svh-2rem)] grid-rows-[auto_minmax(0,1fr)] sm:max-w-2xl">
                <DialogHeader>
                    <DialogTitle>{m.fleet_maintenance_history()}</DialogTitle>
                    <DialogDescription>
                        {vehicle
                            ? m.fleet_maintenance_history_description({
                                  vehicle: `${vehicle.brand} ${vehicle.model} (${vehicle.licensePlate})`,
                              })
                            : ""}
                    </DialogDescription>
                </DialogHeader>

                <ScrollArea className="min-h-0 pr-2">
                    {vehicle && (
                        <div className="space-y-6 mx-2">
                            {isPending ? (
                                <HistorySkeleton />
                            ) : isError ? (
                                <div className="rounded-xl border border-destructive p-4 text-sm text-destructive">
                                    {m.fleet_maintenance_history_error()}
                                </div>
                            ) : (
                                <>
                                    <UpcomingMaintenance
                                        vehicle={vehicle}
                                        latestMaintenance={data[0] ?? null}
                                    />
                                    <section className="space-y-3">
                                        <h2 className="font-heading font-medium">
                                            {m.fleet_completed_maintenance()}
                                        </h2>
                                        <MaintenanceTimeline history={data} />
                                    </section>
                                </>
                            )}
                        </div>
                    )}
                </ScrollArea>
            </DialogContent>
        </Dialog>
    );
}
