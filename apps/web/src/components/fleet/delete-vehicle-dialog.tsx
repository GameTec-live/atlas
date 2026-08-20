import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Spinner } from "@/components/ui/spinner";
import { m } from "@/paraglide/messages";
import type { FleetVehicle } from "@/queries/fleet";

export function DeleteVehicleDialog({
    vehicle,
    isPending,
    onClose,
    onConfirm,
}: {
    vehicle: FleetVehicle | null;
    isPending: boolean;
    onClose: () => void;
    onConfirm: (vehicle: FleetVehicle) => void;
}) {
    return (
        <AlertDialog
            open={vehicle !== null}
            onOpenChange={(open) => {
                if (!open && !isPending) onClose();
            }}
        >
            <AlertDialogContent>
                <AlertDialogHeader>
                    <AlertDialogTitle>
                        {m.fleet_delete_vehicle_title()}
                    </AlertDialogTitle>
                    <AlertDialogDescription>
                        {vehicle
                            ? m.fleet_delete_vehicle_description({
                                  vehicle: `${vehicle.brand} ${vehicle.model} (${vehicle.licensePlate})`,
                              })
                            : ""}
                    </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                    <AlertDialogCancel disabled={isPending}>
                        {m.fleet_cancel()}
                    </AlertDialogCancel>
                    <AlertDialogAction
                        variant="destructive"
                        disabled={isPending}
                        onClick={() => {
                            if (vehicle) onConfirm(vehicle);
                        }}
                    >
                        {isPending && <Spinner />}
                        {m.fleet_delete_vehicle()}
                    </AlertDialogAction>
                </AlertDialogFooter>
            </AlertDialogContent>
        </AlertDialog>
    );
}
