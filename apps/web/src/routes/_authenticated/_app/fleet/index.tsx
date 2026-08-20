import {
    useMutation,
    useQueryClient,
    useSuspenseQuery,
} from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { PlusIcon, SearchIcon } from "lucide-react";
import { useCallback, useState } from "react";
import { DeleteVehicleDialog } from "@/components/fleet/delete-vehicle-dialog";
import { FleetTable } from "@/components/fleet/fleet-table";
import { MaintenanceDialog } from "@/components/fleet/maintenance-dialog";
import { MaintenanceHistoryDialog } from "@/components/fleet/maintenance-history-dialog";
import { VehicleFormDialog } from "@/components/fleet/vehicle-form-dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { toast } from "@/components/ui/toast";
import { api, unwrapEden } from "@/lib/api-client";
import type { MaintenanceInput, VehicleInput } from "@/lib/fleet";
import { m } from "@/paraglide/messages";
import {
    type FleetVehicle,
    fleetQueryKey,
    fleetQueryOptions,
} from "@/queries/fleet";

export const Route = createFileRoute("/_authenticated/_app/fleet/")({
    loader: ({ context }) =>
        context.queryClient.ensureQueryData(fleetQueryOptions()),
    pendingComponent: FleetPageSkeleton,
    component: FleetPage,
});

function FleetPage() {
    const { data } = useSuspenseQuery(fleetQueryOptions());
    const queryClient = useQueryClient();
    const [search, setSearch] = useState("");
    const [isCreateOpen, setIsCreateOpen] = useState(false);
    const [vehicleToEdit, setVehicleToEdit] = useState<FleetVehicle | null>(
        null,
    );
    const [vehicleToMaintain, setVehicleToMaintain] =
        useState<FleetVehicle | null>(null);
    const [vehicleForHistory, setVehicleForHistory] =
        useState<FleetVehicle | null>(null);
    const [vehicleToDelete, setVehicleToDelete] = useState<FleetVehicle | null>(
        null,
    );

    const refreshFleet = useCallback(
        () => queryClient.invalidateQueries({ queryKey: fleetQueryKey }),
        [queryClient],
    );

    const createMutation = useMutation({
        mutationFn: (input: VehicleInput) =>
            unwrapEden(api.fleet.vehicles.post(input)),
        onSuccess: async () => {
            setIsCreateOpen(false);
            await refreshFleet();
            toast.add({
                id: "fleet-create",
                type: "success",
                title: m.fleet_create_success(),
            });
        },
        onError: () => {
            toast.add({
                id: "fleet-create-error",
                type: "error",
                title: m.fleet_create_error(),
                priority: "high",
            });
        },
    });

    const updateMutation = useMutation({
        mutationFn: ({ id, input }: { id: string; input: VehicleInput }) =>
            unwrapEden(api.fleet.vehicles({ id }).put(input)),
        onSuccess: async () => {
            setVehicleToEdit(null);
            await refreshFleet();
            toast.add({
                id: "fleet-update",
                type: "success",
                title: m.fleet_update_success(),
            });
        },
        onError: () => {
            toast.add({
                id: "fleet-update-error",
                type: "error",
                title: m.fleet_update_error(),
                priority: "high",
            });
        },
    });

    const maintenanceMutation = useMutation({
        mutationFn: ({ id, input }: { id: string; input: MaintenanceInput }) =>
            unwrapEden(api.fleet.vehicles({ id }).maintenance.post(input)),
        onSuccess: async () => {
            setVehicleToMaintain(null);
            await refreshFleet();
            toast.add({
                id: "fleet-maintenance",
                type: "success",
                title: m.fleet_maintenance_success(),
            });
        },
        onError: () => {
            toast.add({
                id: "fleet-maintenance-error",
                type: "error",
                title: m.fleet_maintenance_error(),
                priority: "high",
            });
        },
    });

    const deleteMutation = useMutation({
        mutationFn: (id: string) =>
            unwrapEden(api.fleet.vehicles({ id }).delete()),
        onSuccess: async () => {
            setVehicleToDelete(null);
            await refreshFleet();
            toast.add({
                id: "fleet-delete",
                type: "success",
                title: m.fleet_delete_success(),
            });
        },
        onError: () => {
            toast.add({
                id: "fleet-delete-error",
                type: "error",
                title: m.fleet_delete_error(),
                priority: "high",
            });
        },
    });

    const handleMaintenance = useCallback((vehicle: FleetVehicle) => {
        setVehicleToMaintain(vehicle);
    }, []);
    const handleEdit = useCallback((vehicle: FleetVehicle) => {
        setVehicleToEdit(vehicle);
    }, []);
    const handleDelete = useCallback((vehicle: FleetVehicle) => {
        setVehicleToDelete(vehicle);
    }, []);

    return (
        <main className="flex h-full min-h-0 flex-col gap-4 p-4">
            <h1 className="font-heading text-xl font-semibold tracking-tight">
                {m.fleet_title()}
            </h1>

            <div className="flex gap-2">
                <div className="relative min-w-0 flex-1">
                    <SearchIcon className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
                    <Input
                        className="h-10 pl-9"
                        value={search}
                        onChange={(event) => setSearch(event.target.value)}
                        placeholder={m.fleet_search_placeholder()}
                        aria-label={m.fleet_search_label()}
                    />
                </div>
                <Button
                    className="h-10 pr-4"
                    onClick={() => setIsCreateOpen(true)}
                >
                    <PlusIcon />
                    <span className="hidden sm:inline">
                        {m.fleet_new_vehicle()}
                    </span>
                    <span className="sm:hidden">{m.fleet_new()}</span>
                </Button>
            </div>

            <FleetTable
                data={data}
                search={search}
                onSearchChange={setSearch}
                onViewMaintenance={setVehicleForHistory}
                onMaintenance={handleMaintenance}
                onEdit={handleEdit}
                onDelete={handleDelete}
            />

            <VehicleFormDialog
                key={vehicleToEdit?.id ?? "create-vehicle"}
                open={isCreateOpen || vehicleToEdit !== null}
                vehicle={vehicleToEdit}
                onOpenChange={(open) => {
                    if (!open) {
                        setIsCreateOpen(false);
                        setVehicleToEdit(null);
                    }
                }}
                onSubmit={(input) =>
                    vehicleToEdit
                        ? updateMutation
                              .mutateAsync({
                                  id: vehicleToEdit.id,
                                  input,
                              })
                              .then(() => undefined)
                        : createMutation
                              .mutateAsync(input)
                              .then(() => undefined)
                }
            />

            <MaintenanceDialog
                key={vehicleToMaintain?.id ?? "maintenance"}
                vehicle={vehicleToMaintain}
                onClose={() => setVehicleToMaintain(null)}
                onSubmit={(input) => {
                    if (!vehicleToMaintain) return Promise.resolve();
                    return maintenanceMutation
                        .mutateAsync({ id: vehicleToMaintain.id, input })
                        .then(() => undefined);
                }}
            />

            <MaintenanceHistoryDialog
                key={vehicleForHistory?.id ?? "maintenance-history"}
                vehicle={vehicleForHistory}
                onClose={() => setVehicleForHistory(null)}
            />

            <DeleteVehicleDialog
                vehicle={vehicleToDelete}
                isPending={deleteMutation.isPending}
                onClose={() => setVehicleToDelete(null)}
                onConfirm={(vehicle) => deleteMutation.mutate(vehicle.id)}
            />
        </main>
    );
}

function FleetPageSkeleton() {
    return (
        <main className="flex h-full flex-col gap-4 p-4">
            <Skeleton className="h-8 w-44" />
            <div className="flex gap-2">
                <Skeleton className="h-10 flex-1" />
                <Skeleton className="h-10 w-32" />
            </div>
            <Skeleton className="min-h-0 flex-1" />
        </main>
    );
}
