import { queryOptions } from "@tanstack/react-query";
import { api, unwrapEden } from "@/lib/api-client";

export const fleetQueryKey = ["fleet", "vehicles"] as const;

export const maintenanceHistoryQueryKey = (vehicleId: string) =>
    [...fleetQueryKey, vehicleId, "maintenance"] as const;

const fetchFleet = async () => {
    const vehicles = await unwrapEden(api.fleet.vehicles.get());
    return vehicles ?? [];
};

export type FleetRow = Awaited<ReturnType<typeof fetchFleet>>[number];
export type FleetVehicle = FleetRow["vehicle"];

export const fleetQueryOptions = () =>
    queryOptions({
        queryKey: fleetQueryKey,
        queryFn: fetchFleet,
    });

const fetchMaintenanceHistory = async (vehicleId: string) => {
    const history = await unwrapEden(
        api.fleet.vehicles({ id: vehicleId }).maintenance.get(),
    );
    return history ?? [];
};

export type MaintenanceRecord = Awaited<
    ReturnType<typeof fetchMaintenanceHistory>
>[number];

export const maintenanceHistoryQueryOptions = (vehicleId: string) =>
    queryOptions({
        queryKey: maintenanceHistoryQueryKey(vehicleId),
        queryFn: () => fetchMaintenanceHistory(vehicleId),
    });
