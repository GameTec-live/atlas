import { useForm } from "@tanstack/react-form";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { InfoIcon } from "lucide-react";
import { useMemo } from "react";
import * as v from "valibot";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { Spinner } from "@/components/ui/spinner";
import { toast } from "@/components/ui/toast";
import {
    Tooltip,
    TooltipContent,
    TooltipTrigger,
} from "@/components/ui/tooltip";
import { getErrorMessage } from "@/lib/error";
import { getTimezoneOptions } from "@/lib/system";
import { m } from "@/paraglide/messages";
import {
    systemTimezoneMutationOptions,
    systemTimezoneQueryKey,
} from "@/queries/system";

const timezoneSchema = v.object({
    timezone: v.pipe(v.string(), v.minLength(1)),
});

export function SystemTimezoneForm({ timezone }: { timezone: string }) {
    const queryClient = useQueryClient();
    const mutation = useMutation({
        ...systemTimezoneMutationOptions(),
        onSuccess: async () => {
            await queryClient.invalidateQueries({
                queryKey: systemTimezoneQueryKey,
            });
            toast.add({
                id: "system-timezone-saved",
                type: "success",
                title: m.settings_system_timezone_saved(),
            });
        },
        onError: (error) =>
            toast.add({
                id: "system-timezone-error",
                type: "error",
                title: m.settings_system_timezone_error(),
                description: getErrorMessage(error),
            }),
    });
    const timezones = useMemo(() => getTimezoneOptions(timezone), [timezone]);
    const form = useForm({
        defaultValues: { timezone },
        validators: { onChange: timezoneSchema, onSubmit: timezoneSchema },
        onSubmit: ({ value }) => mutation.mutate(value.timezone),
    });

    return (
        <form
            onSubmit={(event) => {
                event.preventDefault();
                void form.handleSubmit();
            }}
            className="flex flex-row gap-4 items-center flex-1 max-w-lg"
        >
            <form.Field name="timezone">
                {(field) => (
                    <Field>
                        <Select
                            value={field.state.value}
                            onValueChange={(value) =>
                                value && field.handleChange(value)
                            }
                        >
                            <SelectTrigger id={field.name}>
                                <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                                {timezones.map((option) => (
                                    <SelectItem key={option} value={option}>
                                        {option}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    </Field>
                )}
            </form.Field>
            <Tooltip>
                <TooltipTrigger aria-label={m.settings_system_timezone_help()}>
                    <InfoIcon className="size-4" />
                </TooltipTrigger>
                <TooltipContent>
                    {m.settings_system_timezone_tooltip()}
                </TooltipContent>
            </Tooltip>
            <form.Subscribe
                selector={(state) => [state.canSubmit, state.isDirty]}
            >
                {([canSubmit, isDirty]) => (
                    <Button
                        type="submit"
                        size="sm"
                        disabled={!canSubmit || !isDirty || mutation.isPending}
                    >
                        {mutation.isPending && <Spinner />}
                        {m.settings_save_changes()}
                    </Button>
                )}
            </form.Subscribe>
        </form>
    );
}
