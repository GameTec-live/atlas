import { useForm } from "@tanstack/react-form";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { PlusIcon, Trash2Icon } from "lucide-react";
import * as v from "valibot";
import { Button } from "@/components/ui/button";
import { Field, FieldError } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { toast } from "@/components/ui/toast";
import { getErrorMessage } from "@/lib/error";
import { m } from "@/paraglide/messages";
import {
    systemAuthOriginMutationOptions,
    systemAuthOriginsQueryKey,
    systemAuthOriginsQueryOptions,
} from "@/queries/system";

const originSchema = v.object({
    origin: v.pipe(
        v.string(),
        v.url("Enter a valid HTTPS origin."),
        v.regex(/^https:\/\/[^/?#]+\/?$/, "Enter an origin without a path."),
    ),
});

export function SystemAuthOrigins() {
    const queryClient = useQueryClient();
    const origins = useQuery(systemAuthOriginsQueryOptions());
    const mutation = useMutation({
        ...systemAuthOriginMutationOptions(),
        onSuccess: async () => {
            await queryClient.invalidateQueries({
                queryKey: systemAuthOriginsQueryKey,
            });
            form.reset();
        },
        onError: (error) =>
            toast.add({
                id: "system-origin-error",
                type: "error",
                title: m.settings_system_connection_error(),
                description: getErrorMessage(error),
                priority: "high",
            }),
    });
    const form = useForm({
        defaultValues: { origin: "" },
        validators: { onChange: originSchema, onSubmit: originSchema },
        onSubmit: ({ value }) =>
            mutation.mutate({ origin: value.origin, action: "add" }),
    });

    return (
        <section className="flex flex-col gap-3">
            <div>
                <h3 className="flex items-center gap-2 font-medium">
                    {m.settings_system_auth_origins()}
                </h3>
                <p className="text-sm text-muted-foreground">
                    {m.settings_system_auth_origins_description()}
                </p>
            </div>
            <form
                className="flex items-start gap-2"
                onSubmit={(event) => {
                    event.preventDefault();
                    void form.handleSubmit();
                }}
            >
                <form.Field name="origin">
                    {(field) => (
                        <Field
                            className="flex-1"
                            data-invalid={!field.state.meta.isValid}
                        >
                            <Input
                                aria-label={m.settings_system_auth_origin()}
                                type="url"
                                placeholder="https://atlas.example.com"
                                value={field.state.value}
                                onBlur={field.handleBlur}
                                onChange={(event) =>
                                    field.handleChange(event.target.value)
                                }
                                aria-invalid={!field.state.meta.isValid}
                            />
                            <FieldError errors={field.state.meta.errors} />
                        </Field>
                    )}
                </form.Field>
                <form.Subscribe selector={(state) => state.values.origin}>
                    {(origin) => (
                        <Button
                            type="submit"
                            disabled={!origin || mutation.isPending}
                        >
                            {mutation.isPending ? <Spinner /> : <PlusIcon />}
                            {m.settings_system_add()}
                        </Button>
                    )}
                </form.Subscribe>
            </form>
            <div className="flex flex-col gap-2">
                {origins.data?.items.length ? (
                    origins.data.items.map((origin) => (
                        <div
                            key={origin}
                            className="flex items-center gap-2 rounded-lg border px-3 py-2"
                        >
                            <span className="min-w-0 flex-1 truncate font-mono text-xs">
                                {origin}
                            </span>
                            <Button
                                type="button"
                                size="icon-sm"
                                variant="ghost"
                                aria-label={m.settings_system_remove_origin({
                                    origin,
                                })}
                                disabled={mutation.isPending}
                                onClick={() =>
                                    mutation.mutate({
                                        origin,
                                        action: "remove",
                                    })
                                }
                            >
                                <Trash2Icon />
                            </Button>
                        </div>
                    ))
                ) : (
                    <p className="text-sm text-muted-foreground">
                        {m.settings_system_no_auth_origins()}
                    </p>
                )}
            </div>
        </section>
    );
}
