import { useForm } from "@tanstack/react-form";
import { Button } from "@/components/ui/button";
import {
    Dialog,
    DialogContent,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { Switch } from "@/components/ui/switch";
import type { VehicleInput } from "@/lib/fleet";
import {
    createVehicleFormSchema,
    type VehicleFormValues,
} from "@/lib/fleet-schema";
import { parseOptionalNumber } from "@/lib/number";
import { m } from "@/paraglide/messages";
import type { FleetVehicle } from "@/queries/fleet";

const toValues = (vehicle: FleetVehicle | null): VehicleFormValues => ({
    brand: vehicle?.brand ?? "",
    model: vehicle?.model ?? "",
    year: vehicle?.year.toString() ?? new Date().getFullYear().toString(),
    licensePlate: vehicle?.licensePlate ?? "",
    fingerprint: vehicle?.fingerprint ?? "",
    odometer: vehicle?.odometer?.toString() ?? "",
    fuelLevel: vehicle?.fuelLevel?.toString() ?? "",
    maintenanceEvery: vehicle?.maintenanceEvery.toString() ?? "20000",
    assessmentMonth: vehicle?.assessmentMonth.toString() ?? "1",
    smartSupport: vehicle?.smartSupport ?? true,
});

export function VehicleFormDialog({
    open,
    vehicle,
    onOpenChange,
    onSubmit,
}: {
    open: boolean;
    vehicle: FleetVehicle | null;
    onOpenChange: (open: boolean) => void;
    onSubmit: (input: VehicleInput) => Promise<void>;
}) {
    const schema = createVehicleFormSchema({
        required: m.fleet_validation_required(),
        number: m.fleet_validation_number(),
        integer: m.fleet_validation_integer(),
    });
    const form = useForm({
        defaultValues: toValues(vehicle),
        validators: {
            onChange: schema,
            onSubmit: schema,
        },
        onSubmit: async ({ value }) => {
            await onSubmit({
                brand: value.brand.trim(),
                model: value.model.trim(),
                year: Number(value.year),
                licensePlate: value.licensePlate.trim(),
                fingerprint:
                    value.fingerprint.trim() || (vehicle ? null : undefined),
                odometer: parseOptionalNumber(value.odometer),
                fuelLevel: parseOptionalNumber(value.fuelLevel),
                maintenanceEvery: Number(value.maintenanceEvery),
                assessmentMonth: Number(value.assessmentMonth),
                smartSupport: value.smartSupport,
            });
            form.reset();
        },
    });

    const title = vehicle
        ? m.fleet_edit_vehicle_title()
        : m.fleet_new_vehicle_title();

    return (
        <Dialog
            open={open}
            onOpenChange={(nextOpen) => {
                if (!nextOpen && form.state.isSubmitting) return;
                if (!nextOpen) form.reset();
                onOpenChange(nextOpen);
            }}
        >
            <DialogContent className="max-h-[calc(100svh-2rem)] overflow-y-auto sm:max-w-2xl">
                <DialogHeader>
                    <DialogTitle>{title}</DialogTitle>
                </DialogHeader>

                <form
                    className="grid gap-5"
                    onSubmit={(event) => {
                        event.preventDefault();
                        event.stopPropagation();
                        void form.handleSubmit();
                    }}
                >
                    <div className="grid gap-4 sm:grid-cols-2">
                        <form.Field name="brand">
                            {(field) => (
                                <Field data-invalid={!field.state.meta.isValid}>
                                    <FieldLabel htmlFor={field.name}>
                                        {m.fleet_brand()}
                                    </FieldLabel>
                                    <Input
                                        id={field.name}
                                        name={field.name}
                                        value={field.state.value}
                                        onBlur={field.handleBlur}
                                        onChange={(event) =>
                                            field.handleChange(
                                                event.target.value,
                                            )
                                        }
                                        aria-invalid={!field.state.meta.isValid}
                                        required
                                    />
                                    <FieldError
                                        errors={field.state.meta.errors}
                                    />
                                </Field>
                            )}
                        </form.Field>

                        <form.Field name="model">
                            {(field) => (
                                <Field data-invalid={!field.state.meta.isValid}>
                                    <FieldLabel htmlFor={field.name}>
                                        {m.fleet_model()}
                                    </FieldLabel>
                                    <Input
                                        id={field.name}
                                        name={field.name}
                                        value={field.state.value}
                                        onBlur={field.handleBlur}
                                        onChange={(event) =>
                                            field.handleChange(
                                                event.target.value,
                                            )
                                        }
                                        aria-invalid={!field.state.meta.isValid}
                                        required
                                    />
                                    <FieldError
                                        errors={field.state.meta.errors}
                                    />
                                </Field>
                            )}
                        </form.Field>

                        <form.Field name="year">
                            {(field) => (
                                <Field data-invalid={!field.state.meta.isValid}>
                                    <FieldLabel htmlFor={field.name}>
                                        {m.fleet_year()}
                                    </FieldLabel>
                                    <Input
                                        id={field.name}
                                        name={field.name}
                                        type="number"
                                        min={1800}
                                        step={1}
                                        value={field.state.value}
                                        onBlur={field.handleBlur}
                                        onChange={(event) =>
                                            field.handleChange(
                                                event.target.value,
                                            )
                                        }
                                        aria-invalid={!field.state.meta.isValid}
                                        required
                                    />
                                    <FieldError
                                        errors={field.state.meta.errors}
                                    />
                                </Field>
                            )}
                        </form.Field>

                        <form.Field name="licensePlate">
                            {(field) => (
                                <Field data-invalid={!field.state.meta.isValid}>
                                    <FieldLabel htmlFor={field.name}>
                                        {m.fleet_license_plate()}
                                    </FieldLabel>
                                    <Input
                                        id={field.name}
                                        name={field.name}
                                        value={field.state.value}
                                        onBlur={field.handleBlur}
                                        onChange={(event) =>
                                            field.handleChange(
                                                event.target.value,
                                            )
                                        }
                                        aria-invalid={!field.state.meta.isValid}
                                        required
                                    />
                                    <FieldError
                                        errors={field.state.meta.errors}
                                    />
                                </Field>
                            )}
                        </form.Field>

                        <form.Field name="fingerprint">
                            {(field) => (
                                <Field>
                                    <FieldLabel htmlFor={field.name}>
                                        {m.fleet_fingerprint()}
                                    </FieldLabel>
                                    <Input
                                        id={field.name}
                                        name={field.name}
                                        value={field.state.value}
                                        onBlur={field.handleBlur}
                                        onChange={(event) =>
                                            field.handleChange(
                                                event.target.value,
                                            )
                                        }
                                        placeholder={m.fleet_optional()}
                                    />
                                </Field>
                            )}
                        </form.Field>

                        <form.Field name="odometer">
                            {(field) => (
                                <Field data-invalid={!field.state.meta.isValid}>
                                    <FieldLabel htmlFor={field.name}>
                                        {m.fleet_odometer()}
                                    </FieldLabel>
                                    <Input
                                        id={field.name}
                                        name={field.name}
                                        type="number"
                                        min={0}
                                        value={field.state.value}
                                        onBlur={field.handleBlur}
                                        onChange={(event) =>
                                            field.handleChange(
                                                event.target.value,
                                            )
                                        }
                                        placeholder={m.fleet_optional()}
                                        aria-invalid={!field.state.meta.isValid}
                                    />
                                    <FieldError
                                        errors={field.state.meta.errors}
                                    />
                                </Field>
                            )}
                        </form.Field>

                        <form.Field name="fuelLevel">
                            {(field) => (
                                <Field data-invalid={!field.state.meta.isValid}>
                                    <FieldLabel htmlFor={field.name}>
                                        {m.fleet_fuel_level()}
                                    </FieldLabel>
                                    <Input
                                        id={field.name}
                                        name={field.name}
                                        type="number"
                                        min={0}
                                        max={100}
                                        value={field.state.value}
                                        onBlur={field.handleBlur}
                                        onChange={(event) =>
                                            field.handleChange(
                                                event.target.value,
                                            )
                                        }
                                        placeholder={m.fleet_optional()}
                                        aria-invalid={!field.state.meta.isValid}
                                    />
                                    <FieldError
                                        errors={field.state.meta.errors}
                                    />
                                </Field>
                            )}
                        </form.Field>

                        <form.Field name="maintenanceEvery">
                            {(field) => (
                                <Field data-invalid={!field.state.meta.isValid}>
                                    <FieldLabel htmlFor={field.name}>
                                        {m.fleet_maintenance_every()}
                                    </FieldLabel>
                                    <Input
                                        id={field.name}
                                        name={field.name}
                                        type="number"
                                        min={0}
                                        value={field.state.value}
                                        onBlur={field.handleBlur}
                                        onChange={(event) =>
                                            field.handleChange(
                                                event.target.value,
                                            )
                                        }
                                        aria-invalid={!field.state.meta.isValid}
                                        required
                                    />
                                    <FieldError
                                        errors={field.state.meta.errors}
                                    />
                                </Field>
                            )}
                        </form.Field>

                        <form.Field name="assessmentMonth">
                            {(field) => (
                                <Field data-invalid={!field.state.meta.isValid}>
                                    <FieldLabel htmlFor={field.name}>
                                        {m.fleet_assessment_month()}
                                    </FieldLabel>
                                    <Input
                                        id={field.name}
                                        name={field.name}
                                        type="number"
                                        min={1}
                                        max={12}
                                        step={1}
                                        value={field.state.value}
                                        onBlur={field.handleBlur}
                                        onChange={(event) =>
                                            field.handleChange(
                                                event.target.value,
                                            )
                                        }
                                        aria-invalid={!field.state.meta.isValid}
                                        required
                                    />
                                    <FieldError
                                        errors={field.state.meta.errors}
                                    />
                                </Field>
                            )}
                        </form.Field>
                    </div>

                    <form.Field name="smartSupport">
                        {(field) => (
                            <Field orientation="horizontal">
                                <div className="flex-1">
                                    <FieldLabel htmlFor={field.name}>
                                        {m.fleet_smart_support()}
                                    </FieldLabel>
                                    <p className="text-sm text-muted-foreground">
                                        {m.fleet_smart_support_description()}
                                    </p>
                                </div>
                                <Switch
                                    id={field.name}
                                    checked={field.state.value}
                                    onCheckedChange={field.handleChange}
                                />
                            </Field>
                        )}
                    </form.Field>

                    <DialogFooter>
                        <Button
                            type="button"
                            variant="outline"
                            disabled={form.state.isSubmitting}
                            onClick={() => onOpenChange(false)}
                        >
                            {m.fleet_cancel()}
                        </Button>
                        <form.Subscribe
                            selector={(state) => [
                                state.canSubmit,
                                state.isSubmitting,
                            ]}
                        >
                            {([canSubmit, isSubmitting]) => (
                                <Button
                                    type="submit"
                                    disabled={!canSubmit || isSubmitting}
                                >
                                    {isSubmitting && <Spinner />}
                                    {vehicle
                                        ? m.fleet_save_vehicle()
                                        : m.fleet_create_vehicle()}
                                </Button>
                            )}
                        </form.Subscribe>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    );
}
