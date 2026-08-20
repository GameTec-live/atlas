import { useForm } from "@tanstack/react-form";
import { Button } from "@/components/ui/button";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { Textarea } from "@/components/ui/textarea";
import type { MaintenanceInput } from "@/lib/fleet";
import { createMaintenanceFormSchema } from "@/lib/fleet-schema";
import { parseOptionalNumber } from "@/lib/number";
import { m } from "@/paraglide/messages";
import type { FleetVehicle } from "@/queries/fleet";

export function MaintenanceDialog({
    vehicle,
    onClose,
    onSubmit,
}: {
    vehicle: FleetVehicle | null;
    onClose: () => void;
    onSubmit: (input: MaintenanceInput) => Promise<void>;
}) {
    const schema = createMaintenanceFormSchema(m.fleet_validation_number());
    const form = useForm({
        defaultValues: {
            odometer: vehicle?.odometer?.toString() ?? "",
            mechanic: "",
            note: "",
        },
        validators: {
            onChange: schema,
            onSubmit: schema,
        },
        onSubmit: async ({ value }) => {
            await onSubmit({
                odometer: parseOptionalNumber(value.odometer),
                mechanic: value.mechanic.trim() || undefined,
                note: value.note.trim() || undefined,
            });
            form.reset();
        },
    });

    return (
        <Dialog
            open={vehicle !== null}
            onOpenChange={(open) => {
                if (!open && !form.state.isSubmitting) {
                    form.reset();
                    onClose();
                }
            }}
        >
            <DialogContent className="sm:max-w-lg">
                <DialogHeader>
                    <DialogTitle>{m.fleet_maintenance_title()}</DialogTitle>
                    <DialogDescription>
                        {vehicle
                            ? m.fleet_maintenance_description({
                                  vehicle: `${vehicle.brand} ${vehicle.model} · ${vehicle.licensePlate}`,
                              })
                            : ""}
                    </DialogDescription>
                </DialogHeader>

                <form
                    className="grid gap-5"
                    onSubmit={(event) => {
                        event.preventDefault();
                        event.stopPropagation();
                        void form.handleSubmit();
                    }}
                >
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
                                        field.handleChange(event.target.value)
                                    }
                                    placeholder={m.fleet_optional()}
                                    aria-invalid={!field.state.meta.isValid}
                                />
                                <FieldError errors={field.state.meta.errors} />
                            </Field>
                        )}
                    </form.Field>

                    <form.Field name="mechanic">
                        {(field) => (
                            <Field>
                                <FieldLabel htmlFor={field.name}>
                                    {m.fleet_mechanic()}
                                </FieldLabel>
                                <Input
                                    id={field.name}
                                    name={field.name}
                                    value={field.state.value}
                                    onBlur={field.handleBlur}
                                    onChange={(event) =>
                                        field.handleChange(event.target.value)
                                    }
                                    placeholder={m.fleet_optional()}
                                />
                            </Field>
                        )}
                    </form.Field>

                    <form.Field name="note">
                        {(field) => (
                            <Field>
                                <FieldLabel htmlFor={field.name}>
                                    {m.fleet_note()}
                                </FieldLabel>
                                <Textarea
                                    id={field.name}
                                    name={field.name}
                                    value={field.state.value}
                                    onBlur={field.handleBlur}
                                    onChange={(event) =>
                                        field.handleChange(event.target.value)
                                    }
                                    placeholder={m.fleet_maintenance_note_placeholder()}
                                />
                            </Field>
                        )}
                    </form.Field>

                    <DialogFooter>
                        <Button
                            type="button"
                            variant="outline"
                            disabled={form.state.isSubmitting}
                            onClick={onClose}
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
                                    {m.fleet_create_maintenance()}
                                </Button>
                            )}
                        </form.Subscribe>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    );
}
