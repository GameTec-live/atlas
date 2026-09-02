import { useMutation } from "@tanstack/react-query";
import { type LucideIcon, PowerIcon, RotateCwIcon } from "lucide-react";
import { useState } from "react";
import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle,
    AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { toast } from "@/components/ui/toast";
import { getErrorMessage } from "@/lib/error";
import { m } from "@/paraglide/messages";
import { systemPowerMutationOptions } from "@/queries/system";

type PowerAction = "restart" | "poweroff";

const actions = {
    restart: {
        icon: RotateCwIcon,
        label: m.settings_system_restart,
        title: m.settings_system_restart_title,
        description: m.settings_system_restart_description,
        success: m.settings_system_restarting,
        error: m.settings_system_restart_error,
    },
    poweroff: {
        icon: PowerIcon,
        label: m.settings_system_poweroff,
        title: m.settings_system_poweroff_title,
        description: m.settings_system_poweroff_description,
        success: m.settings_system_powering_off,
        error: m.settings_system_poweroff_error,
    },
} as const;

function PowerActionButton({ action }: { action: PowerAction }) {
    const [open, setOpen] = useState(false);
    const content = actions[action];
    const Icon: LucideIcon = content.icon;
    const mutation = useMutation({
        ...systemPowerMutationOptions(),
        onSuccess: () => {
            setOpen(false);
            toast.add({
                id: `system-${action}`,
                type: "success",
                title: content.success(),
            });
            setTimeout(() => {
                window.location.reload();
            }, 5000);
        },
        onError: (error) =>
            toast.add({
                id: `system-${action}-error`,
                type: "error",
                title: content.error(),
                description: getErrorMessage(error),
                priority: "high",
            }),
    });

    return (
        <AlertDialog open={open} onOpenChange={setOpen}>
            <AlertDialogTrigger render={<Button variant="outline" size="sm" />}>
                <Icon />
                {content.label()}
            </AlertDialogTrigger>
            <AlertDialogContent>
                <AlertDialogHeader>
                    <AlertDialogTitle>{content.title()}</AlertDialogTitle>
                    <AlertDialogDescription>
                        {content.description()}
                    </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                    <AlertDialogCancel disabled={mutation.isPending}>
                        {m.settings_cancel()}
                    </AlertDialogCancel>
                    <AlertDialogAction
                        variant={
                            action === "poweroff" ? "destructive" : "default"
                        }
                        disabled={mutation.isPending}
                        onClick={() => mutation.mutate(action)}
                    >
                        {mutation.isPending && <Spinner />}
                        {content.label()}
                    </AlertDialogAction>
                </AlertDialogFooter>
            </AlertDialogContent>
        </AlertDialog>
    );
}

export function SystemPowerControls() {
    return (
        <div className="flex flex-wrap gap-2">
            <PowerActionButton action="restart" />
            <PowerActionButton action="poweroff" />
        </div>
    );
}
