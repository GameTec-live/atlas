import { DownloadIcon } from "lucide-react";
import { useState } from "react";
import { SystemUpdateAdvanced } from "@/components/settings/system/system-update-advanced";
import { SystemUpdateGithubForm } from "@/components/settings/system/system-update-github-form";
import { Button } from "@/components/ui/button";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogHeader,
    DialogTitle,
    DialogTrigger,
} from "@/components/ui/dialog";
import { toast } from "@/components/ui/toast";
import { getErrorMessage } from "@/lib/error";
import { formatSystemState } from "@/lib/system";
import { m } from "@/paraglide/messages";
import type { SystemUpdate } from "@/queries/system";

function showUpdateError(error: unknown) {
    toast.add({
        id: "system-update-error",
        type: "error",
        title: m.settings_system_update_error(),
        description: getErrorMessage(error),
        priority: "high",
    });
}

export function SystemUpdateDialog({ update }: { update: SystemUpdate }) {
    const [open, setOpen] = useState(false);
    const updateStarted = () => {
        toast.add({
            id: "system-update-started",
            type: "success",
            title: m.settings_system_update_started(),
            description: m.settings_system_update_rebooting(),
        });
        setOpen(false);
    };

    return (
        <Dialog open={open} onOpenChange={setOpen}>
            <DialogTrigger render={<Button variant="outline" size="sm" />}>
                {m.settings_system_manage_updates()}
            </DialogTrigger>
            <DialogContent className="max-h-11/12 overflow-y-auto sm:max-w-lg">
                <DialogHeader>
                    <DialogTitle className="flex items-center gap-2">
                        <DownloadIcon className="size-4" />
                        {m.settings_system_updates()}
                    </DialogTitle>
                    <DialogDescription>
                        {m.settings_system_updates_description()}
                    </DialogDescription>
                </DialogHeader>
                <dl className="grid grid-cols-2 gap-2 text-sm">
                    <div>
                        <dt className="text-xs text-muted-foreground">
                            {m.settings_system_active_slot()}
                        </dt>
                        <dd className="font-mono">{update.update.active}</dd>
                    </div>
                    <div>
                        <dt className="text-xs text-muted-foreground">
                            {m.settings_system_update_state()}
                        </dt>
                        <dd className="font-medium capitalize">
                            {formatSystemState(update.monitor.phase)}
                        </dd>
                    </div>
                </dl>
                <SystemUpdateGithubForm
                    onSuccess={updateStarted}
                    onError={showUpdateError}
                />
                <SystemUpdateAdvanced
                    update={update}
                    onInstalled={updateStarted}
                    onError={showUpdateError}
                    onRolledBack={() => setOpen(false)}
                />
            </DialogContent>
        </Dialog>
    );
}
