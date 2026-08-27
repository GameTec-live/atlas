import { formatDate } from "@/lib/date";
import { formatNumber } from "@/lib/number";

const unavailable = "-";

export type VehicleInput = {
    brand: string;
    model: string;
    year: number;
    licensePlate: string;
    fingerprint?: string | null;
    odometer?: number;
    fuelLevel?: number;
    maintenanceEvery: number;
    assessmentMonth: number;
    smartSupport: boolean;
};

export type MaintenanceInput = {
    note?: string;
    odometer?: number;
    mechanic?: string;
};

type MaintenanceSummary = {
    odometer: number | null;
    createdAt: Date;
};

type FleetMaintenanceRow = {
    vehicle: {
        maintenanceEvery: number;
        odometer: number | null;
    };
    maintenance: MaintenanceSummary | null;
};

export type UpcomingMaintenance<T> = T & {
    nextOdometer: number;
    remaining: number | null;
};

export function formatOdometer(value: number | null, locale: string) {
    return value === null ? unavailable : `${formatNumber(value, locale)} km`;
}

export function formatFuelLevel(value: number | null, locale: string) {
    return value === null ? unavailable : `${formatNumber(value, locale)}%`;
}

export function formatLastMaintenance(
    maintenance: MaintenanceSummary | null,
    locale: string,
) {
    if (!maintenance) return unavailable;

    return `${formatNumber(
        maintenance.odometer,
        locale,
        unavailable,
    )} km · ${formatDate(maintenance.createdAt, locale)}`;
}

export function calculateNextMaintenanceOdometer(
    lastMaintenanceOdometer: number | null,
    maintenanceInterval: number,
) {
    return lastMaintenanceOdometer === null
        ? null
        : lastMaintenanceOdometer + maintenanceInterval;
}

export function getUpcomingMaintenance<T extends FleetMaintenanceRow>(
    fleet: readonly T[],
): UpcomingMaintenance<T>[] {
    return fleet
        .flatMap((row) => {
            const nextOdometer = calculateNextMaintenanceOdometer(
                row.maintenance?.odometer ?? null,
                row.vehicle.maintenanceEvery,
            );
            if (nextOdometer === null) return [];

            const remaining =
                row.vehicle.odometer === null
                    ? null
                    : nextOdometer - row.vehicle.odometer;
            return [{ ...row, nextOdometer, remaining }];
        })
        .sort(
            (left, right) =>
                (left.remaining ?? Number.POSITIVE_INFINITY) -
                (right.remaining ?? Number.POSITIVE_INFINITY),
        );
}

export function formatNextMaintenance(
    lastMaintenanceOdometer: number | null,
    maintenanceInterval: number,
    odometer: number | null,
    locale: string,
) {
    const nextMaintenanceOdometer = calculateNextMaintenanceOdometer(
        lastMaintenanceOdometer,
        maintenanceInterval,
    );

    if (nextMaintenanceOdometer === null) return "-";

    return (
        formatOdometer(nextMaintenanceOdometer, locale) +
        ` (${formatNumber(nextMaintenanceOdometer - (odometer ?? 0), locale)} km)`
    );
}

export function formatMaintenanceInterval(value: number, locale: string) {
    return `${formatNumber(value, locale)} km`;
}

export function formatAssessmentMonth(value: number) {
    return value.toString().padStart(2, "0");
}
