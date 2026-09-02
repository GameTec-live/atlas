import { useForm } from "@tanstack/react-form";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { CloudIcon } from "lucide-react";
import * as v from "valibot";
import { SystemRemoteProvider } from "@/components/settings/system/system-remote-provider";
import { Button } from "@/components/ui/button";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { toast } from "@/components/ui/toast";
import { getErrorMessage } from "@/lib/error";
import { m } from "@/paraglide/messages";
import {
    type SystemRemoteAccess,
    systemRemoteAccessMutationOptions,
    systemRemoteAccessQueryKey,
} from "@/queries/system";

const schema = v.object({
    token: v.pipe(v.string(), v.minLength(1, "Token is required.")),
    origin: v.string(),
});

export function SystemCloudflareForm({
    status,
}: {
    status: SystemRemoteAccess["cloudflareTunnel"];
}) {
    const queryClient = useQueryClient();
    const mutation = useMutation({
        ...systemRemoteAccessMutationOptions(),
        onSuccess: async () => {
            await queryClient.invalidateQueries({
                queryKey: systemRemoteAccessQueryKey,
            });
            toast.add({
                id: "system-cloudflare-saved",
                type: "success",
                title: m.settings_system_remote_access_saved(),
            });
            form.reset();
        },
        onError: (error) =>
            toast.add({
                id: "system-cloudflare-error",
                type: "error",
                title: m.settings_system_connection_error(),
                description: getErrorMessage(error),
                priority: "high",
            }),
    });
    const form = useForm({
        defaultValues: { token: "", origin: "" },
        validators: { onChange: schema, onSubmit: schema },
        onSubmit: ({ value }) =>
            mutation.mutate({
                provider: "cloudflare",
                action: "provision",
                ...value,
            }),
    });

    return (
        <SystemRemoteProvider
            icon={CloudIcon}
            name="Cloudflare Tunnel"
            status={status}
            pending={mutation.isPending}
            onRemove={() =>
                mutation.mutate({
                    provider: "cloudflare",
                    action: "remove",
                })
            }
        >
            <form
                className="grid gap-3"
                onSubmit={(event) => {
                    event.preventDefault();
                    void form.handleSubmit();
                }}
            >
                <form.Field name="token">
                    {(field) => (
                        <Field data-invalid={!field.state.meta.isValid}>
                            <FieldLabel htmlFor={field.name}>
                                {m.settings_system_tunnel_token()}
                            </FieldLabel>
                            <Input
                                id={field.name}
                                type="password"
                                autoComplete="off"
                                value={field.state.value}
                                onBlur={field.handleBlur}
                                onChange={(event) =>
                                    field.handleChange(event.target.value)
                                }
                            />
                            <FieldError errors={field.state.meta.errors} />
                        </Field>
                    )}
                </form.Field>
                <form.Field name="origin">
                    {(field) => (
                        <Field>
                            <FieldLabel htmlFor={field.name}>
                                {m.settings_system_public_origin_optional()}
                            </FieldLabel>
                            <Input
                                id={field.name}
                                type="url"
                                placeholder="https://atlas.example.com"
                                value={field.state.value}
                                onChange={(event) =>
                                    field.handleChange(event.target.value)
                                }
                            />
                        </Field>
                    )}
                </form.Field>
                <form.Subscribe selector={(state) => state.values.token}>
                    {(token) => (
                        <Button
                            type="submit"
                            disabled={!token || mutation.isPending}
                        >
                            {mutation.isPending && <Spinner />}
                            {m.settings_system_provision()}
                        </Button>
                    )}
                </form.Subscribe>
            </form>
        </SystemRemoteProvider>
    );
}
