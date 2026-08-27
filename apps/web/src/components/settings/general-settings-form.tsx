import { useForm } from "@tanstack/react-form";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import * as v from "valibot";
import { LogoField } from "@/components/settings/logo-field";
import { Button } from "@/components/ui/button";
import {
    Field,
    FieldDescription,
    FieldError,
    FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
    InputGroup,
    InputGroupAddon,
    InputGroupInput,
} from "@/components/ui/input-group";
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
    getValhallaLanguageOptions,
    valhallaLanguageCodes,
} from "@/lib/valhalla-languages";
import { m } from "@/paraglide/messages";
import { getLocale } from "@/paraglide/runtime";
import {
    type SettingsConfig,
    settingsConfigMutationOptions,
    settingsConfigQueryKey,
} from "@/queries/settings";

const settingsSchema = v.object({
    defaultLanguage: v.picklist(valhallaLanguageCodes),
    maxDispatchers: v.pipe(
        v.number(),
        v.integer(m.settings_validation_integer()),
        v.minValue(1, m.settings_validation_dispatchers()),
    ),
    pricePerKilometer: v.pipe(
        v.number(),
        v.minValue(0, m.settings_validation_price()),
    ),
});

export function GeneralSettingsForm({ config }: { config: SettingsConfig }) {
    const queryClient = useQueryClient();
    const languageOptions = getValhallaLanguageOptions(getLocale());
    const saveMutation = useMutation({
        ...settingsConfigMutationOptions(),
        onSuccess: async () => {
            await queryClient.invalidateQueries({
                queryKey: settingsConfigQueryKey,
            });
            toast.add({
                id: "settings-general-saved",
                type: "success",
                title: m.settings_general_saved(),
            });
        },
        onError: () =>
            toast.add({
                id: "settings-general-error",
                type: "error",
                title: m.settings_general_save_error(),
                priority: "high",
            }),
    });
    const form = useForm({
        defaultValues: {
            defaultLanguage: config.routing.defaultLanguage,
            maxDispatchers: config.dispatchers.max,
            pricePerKilometer: config.pricing.pricePerKilometer,
        },
        validators: { onChange: settingsSchema, onSubmit: settingsSchema },
        onSubmit: ({ value }) => saveMutation.mutate(value),
    });

    return (
        <form
            className="grid gap-6 md:grid-cols-[11rem_1fr]"
            onSubmit={(event) => {
                event.preventDefault();
                void form.handleSubmit();
            }}
        >
            <LogoField />
            <div className="grid content-start gap-5 sm:grid-cols-2">
                <form.Field name="maxDispatchers">
                    {(field) => (
                        <Field data-invalid={!field.state.meta.isValid}>
                            <FieldLabel htmlFor={field.name}>
                                {m.settings_max_dispatchers()}
                            </FieldLabel>
                            <Input
                                id={field.name}
                                type="number"
                                min={1}
                                step={1}
                                value={field.state.value}
                                onBlur={field.handleBlur}
                                onChange={(event) =>
                                    field.handleChange(
                                        event.target.valueAsNumber,
                                    )
                                }
                                aria-invalid={!field.state.meta.isValid}
                            />
                            <FieldDescription>
                                {m.settings_max_dispatchers_description()}
                            </FieldDescription>
                            <FieldError errors={field.state.meta.errors} />
                        </Field>
                    )}
                </form.Field>
                <form.Field name="pricePerKilometer">
                    {(field) => (
                        <Field data-invalid={!field.state.meta.isValid}>
                            <FieldLabel htmlFor={field.name}>
                                {m.settings_price_per_km()}
                            </FieldLabel>
                            <InputGroup>
                                <InputGroupInput
                                    id={field.name}
                                    type="number"
                                    min={0}
                                    step="0.01"
                                    value={field.state.value}
                                    onBlur={field.handleBlur}
                                    onChange={(event) =>
                                        field.handleChange(
                                            event.target.valueAsNumber,
                                        )
                                    }
                                    aria-invalid={!field.state.meta.isValid}
                                />
                                <InputGroupAddon align="inline-end">
                                    €/km
                                </InputGroupAddon>
                            </InputGroup>
                            <FieldDescription>
                                {m.settings_price_per_km_description()}
                            </FieldDescription>
                            <FieldError errors={field.state.meta.errors} />
                        </Field>
                    )}
                </form.Field>
                <form.Field name="defaultLanguage">
                    {(field) => (
                        <Field data-invalid={!field.state.meta.isValid}>
                            <FieldLabel htmlFor={field.name}>
                                {m.settings_default_language()}
                            </FieldLabel>
                            <Select
                                value={field.state.value}
                                onValueChange={(value) => {
                                    if (value) field.handleChange(value);
                                }}
                            >
                                <SelectTrigger
                                    id={field.name}
                                    className="w-full"
                                    aria-invalid={!field.state.meta.isValid}
                                >
                                    <SelectValue />
                                </SelectTrigger>
                                <SelectContent>
                                    {languageOptions.map((option) => (
                                        <SelectItem
                                            key={option.value}
                                            value={option.value}
                                        >
                                            {option.label} ({option.value})
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                            <FieldDescription>
                                {m.settings_default_language_description()}
                            </FieldDescription>
                            <FieldError errors={field.state.meta.errors} />
                        </Field>
                    )}
                </form.Field>
                <div className="flex justify-end border-t pt-4 sm:col-span-2">
                    <form.Subscribe
                        selector={(state) => [state.canSubmit, state.isDirty]}
                    >
                        {([canSubmit, isDirty]) => (
                            <Button
                                type="submit"
                                disabled={
                                    !canSubmit ||
                                    !isDirty ||
                                    saveMutation.isPending
                                }
                            >
                                {saveMutation.isPending && <Spinner />}
                                {m.settings_save_changes()}
                            </Button>
                        )}
                    </form.Subscribe>
                </div>
            </div>
        </form>
    );
}
