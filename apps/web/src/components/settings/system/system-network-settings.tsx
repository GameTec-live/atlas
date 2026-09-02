import { useQuery } from "@tanstack/react-query";
import { CableIcon, Settings2Icon } from "lucide-react";
import { useState } from "react";
import { SystemIpSettingsForm } from "@/components/settings/system/system-ip-settings-form";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { m } from "@/paraglide/messages";
import {
    type SystemConnection,
    systemConnectionsQueryOptions,
    systemDevicesQueryOptions,
    systemIpSettingsQueryOptions,
} from "@/queries/system";

const isWired = (connection: SystemConnection) =>
    !/(wifi|wireless|802-11)/i.test(connection.type);

export function SystemNetworkSettings() {
    const [selected, setSelected] = useState<SystemConnection>();
    const connections = useQuery(systemConnectionsQueryOptions());
    const devices = useQuery(systemDevicesQueryOptions());
    const ipSettings = useQuery(systemIpSettingsQueryOptions(selected?.uuid));
    const wiredConnections = connections.data?.items.filter(isWired) ?? [];

    if (connections.isPending || devices.isPending) {
        return <Spinner className="mx-auto my-10" />;
    }
    if (connections.isError || devices.isError) {
        return (
            <p className="text-sm text-destructive">
                {m.settings_system_connections_error()}
            </p>
        );
    }

    return (
        <div className="grid gap-3">
            <div>
                <h3 className="font-medium">
                    {m.settings_system_wired_connections()}
                </h3>
                <p className="text-sm text-muted-foreground">
                    {m.settings_system_wired_connections_description()}
                </p>
            </div>
            {wiredConnections.length ? (
                wiredConnections.map((connection) => {
                    const device = devices.data?.items.find(
                        ({ interface: name }) => name === connection.device,
                    );
                    const isSelected = selected?.uuid === connection.uuid;

                    return (
                        <div
                            key={connection.uuid}
                            className="grid gap-3 rounded-lg border p-3"
                        >
                            <div className="flex flex-wrap items-center gap-3">
                                <CableIcon className="size-4 text-muted-foreground" />
                                <div className="min-w-0 flex-1">
                                    <p className="truncate font-medium">
                                        {connection.name}
                                    </p>
                                    <p className="text-xs text-muted-foreground">
                                        {connection.device ??
                                            m.settings_unknown()}
                                    </p>
                                </div>
                                <Badge variant="outline">
                                    {device?.state ?? m.settings_unknown()}
                                </Badge>
                                <Button
                                    type="button"
                                    size="sm"
                                    variant={
                                        isSelected ? "secondary" : "outline"
                                    }
                                    onClick={() =>
                                        setSelected(
                                            isSelected ? undefined : connection,
                                        )
                                    }
                                >
                                    <Settings2Icon />
                                    {m.settings_system_configure()}
                                </Button>
                            </div>
                            {isSelected &&
                                (ipSettings.isPending ? (
                                    <Spinner className="mx-auto my-6" />
                                ) : ipSettings.data ? (
                                    <SystemIpSettingsForm
                                        key={connection.uuid}
                                        connection={connection}
                                        settings={ipSettings.data}
                                        onSaved={() =>
                                            void ipSettings.refetch()
                                        }
                                    />
                                ) : (
                                    <p className="text-sm text-destructive">
                                        {m.settings_system_ip_error()}
                                    </p>
                                ))}
                        </div>
                    );
                })
            ) : (
                <p className="rounded-lg border border-dashed p-6 text-center text-sm text-muted-foreground">
                    {m.settings_system_no_wired_connections()}
                </p>
            )}
        </div>
    );
}
