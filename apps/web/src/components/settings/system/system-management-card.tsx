import { useQuery } from "@tanstack/react-query";
import {
    BoxIcon,
    ChevronDownIcon,
    Clock3Icon,
    DownloadIcon,
    NetworkIcon,
    PowerIcon,
    ServerIcon,
} from "lucide-react";

import { SystemConnectionsDialog } from "@/components/settings/system/system-connections-dialog";
import { SystemDetailsDialog } from "@/components/settings/system/system-details-dialog";
import { SystemFactoryResetDialog } from "@/components/settings/system/system-factory-reset-dialog";
import { SystemPowerControls } from "@/components/settings/system/system-power-controls";
import { SystemSSHSettings } from "@/components/settings/system/system-ssh-settings";
import { SystemTimezoneForm } from "@/components/settings/system/system-timezone-form";
import { SystemUpdateDialog } from "@/components/settings/system/system-update-dialog";
import { Button } from "@/components/ui/button";
import {
    Card,
    CardContent,
    CardDescription,
    CardFooter,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import {
    Collapsible,
    CollapsibleContent,
    CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { Skeleton } from "@/components/ui/skeleton";
import { formatSystemState } from "@/lib/system";
import { m } from "@/paraglide/messages";
import {
    apiInfoQueryOptions,
    systemConnectionsQueryOptions,
    systemContainersQueryOptions,
    systemRemoteAccessQueryOptions,
    systemTimezoneQueryOptions,
    systemUpdateQueryOptions,
} from "@/queries/system";
import { SystemSummary } from "./system-summary";

export function SystemManagementCard() {
    const info = useQuery(apiInfoQueryOptions());
    const containers = useQuery(systemContainersQueryOptions());
    const update = useQuery(systemUpdateQueryOptions());
    const timezone = useQuery(systemTimezoneQueryOptions());
    const connections = useQuery(systemConnectionsQueryOptions());
    const remote = useQuery(systemRemoteAccessQueryOptions());
    const remoteCount = remote.data
        ? Number(remote.data.cloudflareTunnel.provisioned) +
          Number(remote.data.tailscale.provisioned)
        : 0;

    return (
        <Card className="lg:col-span-12">
            <CardHeader>
                <CardTitle className="flex items-center gap-2">
                    <ServerIcon className="size-4" />
                    {m.settings_system()}
                </CardTitle>
                <CardDescription>
                    {m.settings_system_management_description()}
                </CardDescription>
            </CardHeader>
            <CardContent className="flex flex-col">
                <SystemSummary
                    icon={BoxIcon}
                    title={m.settings_system_details()}
                    description={`${info.data?.build.version ?? m.settings_unknown()}`}
                >
                    {info.data && (
                        <SystemDetailsDialog
                            info={info.data}
                            containers={containers.data ?? undefined}
                            update={update.data ?? undefined}
                        />
                    )}
                </SystemSummary>
                <SystemSummary
                    icon={DownloadIcon}
                    title={m.settings_system_updates()}
                    description={
                        update.data
                            ? formatSystemState(update.data.monitor.phase)
                            : m.settings_unknown()
                    }
                >
                    {update.data && <SystemUpdateDialog update={update.data} />}
                </SystemSummary>
                <SystemSummary
                    icon={NetworkIcon}
                    title={m.settings_system_connection_settings()}
                    description={`${connections.data?.count ?? 0} ${m.settings_system_connections()} - ${remoteCount} ${m.settings_system_remote()}`}
                >
                    <SystemConnectionsDialog />
                </SystemSummary>
                <SystemSummary
                    icon={Clock3Icon}
                    title={m.settings_system_timezone()}
                    description={m.settings_system_timezone_description()}
                >
                    {timezone.data ? (
                        <SystemTimezoneForm
                            key={timezone.data.timezone}
                            timezone={timezone.data.timezone}
                        />
                    ) : (
                        <Skeleton className="h-14" />
                    )}
                </SystemSummary>
                <SystemSummary
                    icon={PowerIcon}
                    title={m.settings_system_power()}
                    description={m.settings_system_power_description()}
                >
                    <SystemPowerControls />
                </SystemSummary>

                <Collapsible>
                    <CollapsibleTrigger
                        render={
                            <Button
                                variant="ghost"
                                className="group w-full justify-between"
                            />
                        }
                    >
                        {m.settings_system_advanced()}
                        <ChevronDownIcon className="transition-transform group-data-panel-open:rotate-180" />
                    </CollapsibleTrigger>
                    <CollapsibleContent className="pt-2">
                        <SystemSSHSettings />
                    </CollapsibleContent>
                </Collapsible>
            </CardContent>
            <CardFooter className="justify-between gap-4">
                <div>
                    <p className="font-medium">
                        {m.settings_system_factory_reset()}
                    </p>
                    <p className="text-xs text-muted-foreground">
                        {m.settings_system_factory_reset_footer()}
                    </p>
                </div>
                <SystemFactoryResetDialog />
            </CardFooter>
        </Card>
    );
}
