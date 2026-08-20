import {
    columnFilteringFeature,
    createColumnHelper,
    createFilteredRowModel,
    filterFn_includesString,
    globalFilteringFeature,
    tableFeatures,
    useTable,
} from "@tanstack/react-table";
import {
    CheckIcon,
    PencilIcon,
    Trash2Icon,
    WrenchIcon,
    XIcon,
} from "lucide-react";
import { useMemo } from "react";
import { Button } from "@/components/ui/button";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
import {
    formatAssessmentMonth,
    formatFuelLevel,
    formatLastMaintenance,
    formatMaintenanceInterval,
    formatNextMaintenance,
    formatOdometer,
} from "@/lib/fleet";
import { m } from "@/paraglide/messages";
import { getLocale } from "@/paraglide/runtime";
import type { FleetRow, FleetVehicle } from "@/queries/fleet";

const features = tableFeatures({
    columnFilteringFeature,
    globalFilteringFeature,
    filteredRowModel: createFilteredRowModel(),
    filterFns: { includesString: filterFn_includesString },
});

const columnHelper = createColumnHelper<typeof features, FleetRow>();

export function FleetTable({
    data,
    search,
    onSearchChange,
    onViewMaintenance,
    onMaintenance,
    onEdit,
    onDelete,
}: {
    data: FleetRow[];
    search: string;
    onSearchChange: (value: string) => void;
    onViewMaintenance: (vehicle: FleetVehicle) => void;
    onMaintenance: (vehicle: FleetVehicle) => void;
    onEdit: (vehicle: FleetVehicle) => void;
    onDelete: (vehicle: FleetVehicle) => void;
}) {
    const locale = getLocale();
    const columns = useMemo(
        () =>
            columnHelper.columns([
                columnHelper.accessor((row) => row.vehicle.id, {
                    id: "id",
                    header: m.fleet_id(),
                    cell: ({ getValue }) => (
                        <span className="font-mono text-xs" title={getValue()}>
                            {getValue().slice(0, 8)}
                        </span>
                    ),
                }),
                columnHelper.accessor((row) => row.vehicle.fingerprint ?? "-", {
                    id: "fingerprint",
                    header: m.fleet_fingerprint(),
                    cell: ({ getValue }) => (
                        <span className="font-mono text-xs" title={getValue()}>
                            {getValue().slice(0, 8)}
                        </span>
                    ),
                }),
                columnHelper.accessor((row) => row.vehicle.brand, {
                    id: "brand",
                    header: m.fleet_brand(),
                }),
                columnHelper.accessor((row) => row.vehicle.model, {
                    id: "model",
                    header: m.fleet_model(),
                }),
                columnHelper.accessor((row) => row.vehicle.year.toString(), {
                    id: "year",
                    header: m.fleet_year(),
                }),
                columnHelper.accessor((row) => row.vehicle.licensePlate, {
                    id: "licensePlate",
                    header: m.fleet_license_plate(),
                    cell: ({ getValue }) => (
                        <span className="font-medium">{getValue()}</span>
                    ),
                }),
                columnHelper.accessor(
                    (row) => formatOdometer(row.vehicle.odometer, locale),
                    {
                        id: "odometer",
                        header: m.fleet_odometer(),
                    },
                ),
                columnHelper.accessor(
                    (row) => formatFuelLevel(row.vehicle.fuelLevel, locale),
                    {
                        id: "fuelLevel",
                        header: m.fleet_fuel_level(),
                    },
                ),
                columnHelper.accessor(
                    (row) => formatLastMaintenance(row.maintenance, locale),
                    {
                        id: "lastMaintenance",
                        header: m.fleet_last_maintenance(),
                        cell: ({ getValue, row }) => (
                            <Button
                                type="button"
                                variant="link"
                                className="h-auto justify-start p-0 text-left font-normal"
                                title={m.fleet_view_maintenance_history()}
                                onClick={() =>
                                    onViewMaintenance(row.original.vehicle)
                                }
                            >
                                {getValue()}
                            </Button>
                        ),
                    },
                ),
                columnHelper.accessor(
                    (row) =>
                        formatNextMaintenance(
                            row.maintenance?.odometer ?? null,
                            row.vehicle.maintenanceEvery,
                            row.vehicle.odometer,
                            locale,
                        ),
                    {
                        id: "nextMaintenance",
                        header: m.fleet_next_maintenance(),
                        cell: ({ getValue, row }) => (
                            <Button
                                type="button"
                                variant="link"
                                className="h-auto justify-start p-0 text-left font-normal"
                                title={m.fleet_view_maintenance_history()}
                                onClick={() =>
                                    onViewMaintenance(row.original.vehicle)
                                }
                            >
                                {getValue()}
                            </Button>
                        ),
                    },
                ),
                columnHelper.accessor(
                    (row) =>
                        formatMaintenanceInterval(
                            row.vehicle.maintenanceEvery,
                            locale,
                        ),
                    {
                        id: "maintenanceEvery",
                        header: m.fleet_maintenance_every(),
                    },
                ),
                columnHelper.accessor(
                    (row) => formatAssessmentMonth(row.vehicle.assessmentMonth),
                    {
                        id: "assessmentMonth",
                        header: m.fleet_assessment_month(),
                    },
                ),
                columnHelper.accessor(
                    (row) =>
                        row.vehicle.smartSupport ? m.fleet_yes() : m.fleet_no(),
                    {
                        id: "smartSupport",
                        header: m.fleet_smart_support(),
                        cell: ({ row, getValue }) => (
                            <div className="flex items-center justify-center gap-2">
                                {row.original.vehicle.smartSupport ? (
                                    <CheckIcon className="h-4 w-4 text-emerald-500" />
                                ) : (
                                    <XIcon className="h-4 w-4 text-destructive" />
                                )}
                                <span>{getValue()}</span>
                            </div>
                        ),
                    },
                ),
                columnHelper.display({
                    id: "actions",
                    header: m.fleet_actions(),
                    cell: ({ row }) => (
                        <div className="flex justify-end gap-1">
                            <Button
                                type="button"
                                variant="ghost"
                                size="icon-sm"
                                aria-label={m.fleet_add_maintenance()}
                                title={m.fleet_add_maintenance()}
                                onClick={() =>
                                    onMaintenance(row.original.vehicle)
                                }
                            >
                                <WrenchIcon />
                            </Button>
                            <Button
                                type="button"
                                variant="ghost"
                                size="icon-sm"
                                aria-label={m.fleet_edit_vehicle()}
                                title={m.fleet_edit_vehicle()}
                                onClick={() => onEdit(row.original.vehicle)}
                            >
                                <PencilIcon />
                            </Button>
                            <Button
                                type="button"
                                variant="ghost"
                                size="icon-sm"
                                className="text-destructive hover:text-destructive"
                                aria-label={m.fleet_delete_vehicle()}
                                title={m.fleet_delete_vehicle()}
                                onClick={() => onDelete(row.original.vehicle)}
                            >
                                <Trash2Icon />
                            </Button>
                        </div>
                    ),
                    enableGlobalFilter: false,
                }),
            ]),
        [locale, onDelete, onEdit, onMaintenance, onViewMaintenance],
    );

    const table = useTable(
        {
            features,
            columns,
            data,
            state: { globalFilter: search },
            onGlobalFilterChange: (updater) => {
                onSearchChange(
                    typeof updater === "function" ? updater(search) : updater,
                );
            },
            globalFilterFn: "includesString",
            getColumnCanGlobalFilter: (column) => column.id !== "actions",
            getRowId: (row) => row.vehicle.id,
        },
        (state) => ({ globalFilter: state.globalFilter }),
    );

    return (
        <div className="min-h-0 flex-1 overflow-auto rounded-xl border">
            <Table>
                <TableHeader className="bg-muted">
                    {table.getHeaderGroups().map((headerGroup) => (
                        <TableRow key={headerGroup.id}>
                            {headerGroup.headers.map((header) => (
                                <TableHead
                                    key={header.id}
                                    className="last:text-right last:sticky last:right-0 last:bg-muted"
                                >
                                    {header.isPlaceholder ? null : (
                                        <table.FlexRender header={header} />
                                    )}
                                </TableHead>
                            ))}
                        </TableRow>
                    ))}
                </TableHeader>
                <TableBody>
                    {table.getRowModel().rows.length ? (
                        table.getRowModel().rows.map((row) => (
                            <TableRow key={row.id} className="group">
                                {row.getAllCells().map((cell) => (
                                    <TableCell
                                        key={cell.id}
                                        className="last:sticky last:right-0 last:bg-background group-hover:last:bg-muted/50"
                                    >
                                        <table.FlexRender cell={cell} />
                                    </TableCell>
                                ))}
                            </TableRow>
                        ))
                    ) : (
                        <TableRow>
                            <TableCell
                                colSpan={columns.length}
                                className="h-32 text-center text-muted-foreground"
                            >
                                {search
                                    ? m.fleet_no_search_results()
                                    : m.fleet_no_vehicles()}
                            </TableCell>
                        </TableRow>
                    )}
                </TableBody>
            </Table>
        </div>
    );
}
