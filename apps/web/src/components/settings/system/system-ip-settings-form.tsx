import { useForm } from "@tanstack/react-form";
import { useMutation } from "@tanstack/react-query";
import * as v from "valibot";
import { Button } from "@/components/ui/button";
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { Spinner } from "@/components/ui/spinner";
import { toast } from "@/components/ui/toast";
import { getErrorMessage } from "@/lib/error";
import { getSystemIpMethod, parseSystemList } from "@/lib/system";
import { m } from "@/paraglide/messages";
import {
    type SystemConnection,
    type SystemIpSettings,
    systemIpSettingsMutationOptions,
} from "@/queries/system";

type IpFamily = "ipv4" | "ipv6";

const ipSchema = v.object({
    ipv4Method: v.picklist(["auto", "manual", "disabled"]),
    ipv4Addresses: v.string(),
    ipv4Gateway: v.string(),
    ipv4Dns: v.string(),
    ipv6Method: v.picklist(["auto", "manual", "disabled"]),
    ipv6Addresses: v.string(),
    ipv6Gateway: v.string(),
    ipv6Dns: v.string(),
});

function useIpForm(
    connection: SystemConnection,
    settings: SystemIpSettings,
    onSaved: () => void,
) {
    const mutation = useMutation({
        ...systemIpSettingsMutationOptions(),
        onSuccess: () => {
            toast.add({
                id: "system-ip-saved",
                type: "success",
                title: m.settings_system_ip_saved(),
            });
            onSaved();
        },
        onError: (error) =>
            toast.add({
                id: "system-connection-error",
                type: "error",
                title: m.settings_system_connection_error(),
                description: getErrorMessage(error),
                priority: "high",
            }),
    });
    const form = useForm({
        defaultValues: {
            ipv4Method: getSystemIpMethod(settings.ipv4.method),
            ipv4Addresses: settings.ipv4.addresses.join(", "),
            ipv4Gateway: settings.ipv4.gateway ?? "",
            ipv4Dns: settings.ipv4.dns.join(", "),
            ipv6Method: getSystemIpMethod(settings.ipv6.method),
            ipv6Addresses: settings.ipv6.addresses.join(", "),
            ipv6Gateway: settings.ipv6.gateway ?? "",
            ipv6Dns: settings.ipv6.dns.join(", "),
        },
        validators: { onChange: ipSchema, onSubmit: ipSchema },
        onSubmit: ({ value }) =>
            mutation.mutate({
                uuid: connection.uuid,
                ipv4: {
                    method: value.ipv4Method,
                    addresses: parseSystemList(value.ipv4Addresses),
                    gateway: value.ipv4Gateway || undefined,
                    dns: parseSystemList(value.ipv4Dns),
                },
                ipv6: {
                    method: value.ipv6Method,
                    addresses: parseSystemList(value.ipv6Addresses),
                    gateway: value.ipv6Gateway || undefined,
                    dns: parseSystemList(value.ipv6Dns),
                },
            }),
    });
    return { form, mutation };
}

function IpFamilyFields({
    form,
    family,
}: {
    form: ReturnType<typeof useIpForm>["form"];
    family: IpFamily;
}) {
    const methodName = `${family}Method` as const;
    const addressesName = `${family}Addresses` as const;
    const gatewayName = `${family}Gateway` as const;
    const dnsName = `${family}Dns` as const;

    return (
        <div className="grid gap-3">
            <form.Field name={methodName}>
                {(field) => (
                    <Field>
                        <FieldLabel htmlFor={field.name}>
                            {family.toUpperCase()}
                        </FieldLabel>
                        <Select
                            value={field.state.value}
                            onValueChange={(value) =>
                                value &&
                                field.handleChange(getSystemIpMethod(value))
                            }
                        >
                            <SelectTrigger id={field.name} className="w-full">
                                <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                                <SelectItem value="auto">
                                    {m.settings_system_ip_automatic()}
                                </SelectItem>
                                <SelectItem value="manual">
                                    {m.settings_system_ip_manual()}
                                </SelectItem>
                                <SelectItem value="disabled">
                                    {m.settings_system_ip_disabled()}
                                </SelectItem>
                            </SelectContent>
                        </Select>
                    </Field>
                )}
            </form.Field>
            <form.Subscribe selector={(state) => state.values[methodName]}>
                {(method) =>
                    method === "manual" && (
                        <div className="grid gap-3 sm:grid-cols-2 mx-4">
                            <form.Field name={addressesName}>
                                {(field) => (
                                    <Field className="sm:col-span-2">
                                        <FieldLabel htmlFor={field.name}>
                                            {m.settings_system_ip_addresses()}
                                        </FieldLabel>
                                        <Input
                                            id={field.name}
                                            value={field.state.value}
                                            onChange={(event) =>
                                                field.handleChange(
                                                    event.target.value,
                                                )
                                            }
                                            placeholder={
                                                family === "ipv4"
                                                    ? "192.168.1.20/24"
                                                    : "2001:db8::20/64"
                                            }
                                        />
                                        <FieldDescription>
                                            {m.settings_system_ip_list_hint()}
                                        </FieldDescription>
                                    </Field>
                                )}
                            </form.Field>
                            <form.Field name={gatewayName}>
                                {(field) => (
                                    <Field>
                                        <FieldLabel htmlFor={field.name}>
                                            {m.settings_system_ip_gateway()}
                                        </FieldLabel>
                                        <Input
                                            id={field.name}
                                            value={field.state.value}
                                            onChange={(event) =>
                                                field.handleChange(
                                                    event.target.value,
                                                )
                                            }
                                            placeholder={
                                                family === "ipv4"
                                                    ? "192.168.1.1"
                                                    : "2001:db8::1"
                                            }
                                        />
                                    </Field>
                                )}
                            </form.Field>
                            <form.Field name={dnsName}>
                                {(field) => (
                                    <Field>
                                        <FieldLabel htmlFor={field.name}>
                                            DNS
                                        </FieldLabel>
                                        <Input
                                            id={field.name}
                                            value={field.state.value}
                                            onChange={(event) =>
                                                field.handleChange(
                                                    event.target.value,
                                                )
                                            }
                                            placeholder="1.1.1.1, 8.8.8.8"
                                        />
                                    </Field>
                                )}
                            </form.Field>
                        </div>
                    )
                }
            </form.Subscribe>
        </div>
    );
}

export function SystemIpSettingsForm({
    connection,
    settings,
    onSaved,
}: {
    connection: SystemConnection;
    settings: SystemIpSettings;
    onSaved: () => void;
}) {
    const { form, mutation } = useIpForm(connection, settings, onSaved);

    return (
        <form
            className="grid gap-6"
            onSubmit={(event) => {
                event.preventDefault();
                void form.handleSubmit();
            }}
        >
            <IpFamilyFields form={form} family="ipv4" />
            <IpFamilyFields form={form} family="ipv6" />
            <div className="flex justify-end">
                <form.Subscribe selector={(state) => state.canSubmit}>
                    {(canSubmit) => (
                        <Button
                            type="submit"
                            disabled={!canSubmit || mutation.isPending}
                        >
                            {mutation.isPending && <Spinner />}
                            {m.settings_save_changes()}
                        </Button>
                    )}
                </form.Subscribe>
            </div>
        </form>
    );
}
