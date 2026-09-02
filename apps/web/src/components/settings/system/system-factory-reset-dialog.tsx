import { useForm } from "@tanstack/react-form";
import { useMutation } from "@tanstack/react-query";
import { ShieldAlertIcon, Trash2Icon } from "lucide-react";
import { useState } from "react";
import * as v from "valibot";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
    DialogTrigger,
} from "@/components/ui/dialog";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { toast } from "@/components/ui/toast";
import { getErrorMessage } from "@/lib/error";
import { m } from "@/paraglide/messages";
import { systemFactoryResetMutationOptions } from "@/queries/system";

const confirmationText = "FACTORY RESET";
const factoryResetSchema = v.object({
    confirmation: v.pipe(
        v.string(),
        v.literal(confirmationText, `Type ${confirmationText} exactly.`),
    ),
    understood: v.literal(true, "Confirm that you understand."),
});

export function SystemFactoryResetDialog() {
    const [open, setOpen] = useState(false);
    const [step, setStep] = useState<1 | 2>(1);
    const resetMutation = useMutation({
        ...systemFactoryResetMutationOptions(),
        onSuccess: () => {
            toast.add({
                id: "system-factory-reset",
                type: "warning",
                title: m.settings_system_factory_reset_scheduled(),
            });
            setOpen(false);
            setTimeout(() => {
                window.location.reload();
            }, 10000);
        },
        onError: (error) =>
            toast.add({
                id: "system-factory-reset-error",
                type: "error",
                title: m.settings_system_factory_reset_error(),
                description: getErrorMessage(error),
                priority: "high",
            }),
    });
    const form = useForm({
        defaultValues: { confirmation: "", understood: false as boolean },
        validators: {
            onChange: factoryResetSchema,
            onSubmit: factoryResetSchema,
        },
        onSubmit: () => resetMutation.mutate(),
    });

    const handleOpenChange = (nextOpen: boolean) => {
        setOpen(nextOpen);
        if (!nextOpen) {
            setStep(1);
            form.reset();
        }
    };

    return (
        <Dialog open={open} onOpenChange={handleOpenChange}>
            <DialogTrigger render={<Button variant="destructive" size="sm" />}>
                <Trash2Icon />
                {m.settings_system_factory_reset()}
            </DialogTrigger>
            <DialogContent showCloseButton={!resetMutation.isPending}>
                {step === 1 ? (
                    <>
                        <DialogHeader>
                            <DialogTitle>
                                {m.settings_system_factory_reset_title()}
                            </DialogTitle>
                        </DialogHeader>
                        <Alert variant="destructive">
                            <ShieldAlertIcon />
                            <AlertTitle>
                                {m.settings_system_factory_reset_warning()}
                            </AlertTitle>
                            <AlertDescription>
                                {m.settings_system_factory_reset_warning_description()}
                            </AlertDescription>
                        </Alert>
                        <DialogFooter>
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => setOpen(false)}
                            >
                                {m.settings_cancel()}
                            </Button>
                            <Button
                                type="button"
                                variant="destructive"
                                onClick={() => setStep(2)}
                            >
                                {m.settings_system_factory_reset_continue()}
                            </Button>
                        </DialogFooter>
                    </>
                ) : (
                    <form
                        className="grid gap-4"
                        onSubmit={(event) => {
                            event.preventDefault();
                            void form.handleSubmit();
                        }}
                    >
                        <DialogHeader>
                            <DialogTitle>
                                {m.settings_system_factory_reset_confirm_title()}
                            </DialogTitle>
                            <DialogDescription>
                                {m.settings_system_factory_reset_confirm_description()}
                            </DialogDescription>
                        </DialogHeader>
                        <form.Field name="confirmation">
                            {(field) => (
                                <Field data-invalid={!field.state.meta.isValid}>
                                    <FieldLabel htmlFor={field.name}>
                                        {m.settings_system_factory_reset_type({
                                            phrase: confirmationText,
                                        })}
                                    </FieldLabel>
                                    <Input
                                        id={field.name}
                                        autoComplete="off"
                                        value={field.state.value}
                                        onBlur={field.handleBlur}
                                        onChange={(event) =>
                                            field.handleChange(
                                                event.target.value,
                                            )
                                        }
                                        aria-invalid={!field.state.meta.isValid}
                                    />
                                    <FieldError
                                        errors={field.state.meta.errors}
                                    />
                                </Field>
                            )}
                        </form.Field>
                        <form.Field name="understood">
                            {(field) => (
                                <Field orientation="horizontal">
                                    <Checkbox
                                        id={field.name}
                                        checked={field.state.value}
                                        onCheckedChange={field.handleChange}
                                    />
                                    <div>
                                        <FieldLabel htmlFor={field.name}>
                                            {m.settings_system_factory_reset_acknowledge()}
                                        </FieldLabel>
                                    </div>
                                </Field>
                            )}
                        </form.Field>
                        <DialogFooter>
                            <Button
                                type="button"
                                variant="outline"
                                disabled={resetMutation.isPending}
                                onClick={() => setStep(1)}
                            >
                                {m.settings_system_back()}
                            </Button>
                            <form.Subscribe
                                selector={(state) => ({
                                    canSubmit: state.canSubmit,
                                    confirmation: state.values.confirmation,
                                    understood: state.values.understood,
                                })}
                            >
                                {({ canSubmit, confirmation, understood }) => (
                                    <Button
                                        type="submit"
                                        variant="destructive"
                                        disabled={
                                            !canSubmit ||
                                            confirmation !== confirmationText ||
                                            !understood ||
                                            resetMutation.isPending
                                        }
                                    >
                                        {resetMutation.isPending && <Spinner />}
                                        {m.settings_system_factory_reset_confirm()}
                                    </Button>
                                )}
                            </form.Subscribe>
                        </DialogFooter>
                    </form>
                )}
            </DialogContent>
        </Dialog>
    );
}
