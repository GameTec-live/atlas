import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { KeyRoundIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { toast } from "@/components/ui/toast";
import { getErrorMessage } from "@/lib/error";
import { m } from "@/paraglide/messages";
import {
    systemSshMutationOptions,
    systemSshQueryKey,
    systemSshQueryOptions,
} from "@/queries/system";
import { SystemSummary } from "./system-summary";

export function SystemSSHSettings() {
    const ssh = useQuery(systemSshQueryOptions());
    const queryClient = useQueryClient();
    const mutation = useMutation({
        ...systemSshMutationOptions(),
        onSuccess: async () => {
            await queryClient.invalidateQueries({
                queryKey: systemSshQueryKey,
            });
            toast.add({
                id: "system-ssh-saved",
                type: "success",
                title: m.settings_system_ssh_saved(),
            });
        },
        onError: (error) =>
            toast.add({
                id: "system-ssh-error",
                type: "error",
                title: m.settings_system_ssh_error(),
                description: getErrorMessage(error),
            }),
    });
    const enabled = ssh.data?.enabled ?? false;

    return (
        <SystemSummary
            icon={KeyRoundIcon}
            title={m.settings_system_ssh_access()}
            description={
                enabled
                    ? m.spicy_fresh_vulture_learn({ username: "atlas" })
                    : m.settings_system_ssh_disclaimer()
            }
        >
            <Button
                type="button"
                size="sm"
                variant={enabled ? "destructive" : "outline"}
                disabled={!ssh.data || mutation.isPending}
                onClick={() => mutation.mutate(!enabled)}
            >
                {mutation.isPending && <Spinner />}
                {enabled
                    ? m.settings_system_disable_ssh()
                    : m.settings_system_enable_ssh()}
            </Button>
        </SystemSummary>
    );
}
