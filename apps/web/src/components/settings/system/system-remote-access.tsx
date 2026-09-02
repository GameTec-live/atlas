import { useQuery } from "@tanstack/react-query";
import { SystemAuthOrigins } from "@/components/settings/system/system-auth-origins";
import { SystemCloudflareForm } from "@/components/settings/system/system-cloudflare-form";
import { SystemTailscaleForm } from "@/components/settings/system/system-tailscale-form";
import { Separator } from "@/components/ui/separator";
import { Spinner } from "@/components/ui/spinner";
import { m } from "@/paraglide/messages";
import { systemRemoteAccessQueryOptions } from "@/queries/system";

export function SystemRemoteAccess() {
    const remote = useQuery(systemRemoteAccessQueryOptions());

    if (remote.isPending) return <Spinner className="mx-auto my-10" />;
    if (remote.isError || !remote.data) {
        return (
            <p className="text-sm text-destructive">
                {m.settings_system_connection_error()}
            </p>
        );
    }

    return (
        <div className="grid gap-4">
            <div>
                <h3 className="font-medium">
                    {m.settings_system_remote_access()}
                </h3>
                <p className="text-sm text-muted-foreground">
                    {m.settings_system_remote_access_description()}
                </p>
            </div>
            <div className="grid gap-3 lg:grid-cols-2">
                <SystemCloudflareForm status={remote.data.cloudflareTunnel} />
                <SystemTailscaleForm status={remote.data.tailscale} />
            </div>
            <Separator />
            <SystemAuthOrigins />
        </div>
    );
}
