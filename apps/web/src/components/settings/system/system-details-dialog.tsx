import {
    BoxesIcon,
    BoxIcon,
    ContainerIcon,
    CuboidIcon,
    GitCommitHorizontalIcon,
    HardDriveIcon,
    HeartPulseIcon,
    type LucideIcon,
    ServerIcon,
    UndoDotIcon,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogHeader,
    DialogTitle,
    DialogTrigger,
} from "@/components/ui/dialog";
import { Separator } from "@/components/ui/separator";
import { formatDateTime } from "@/lib/date";
import { m } from "@/paraglide/messages";
import { getLocale } from "@/paraglide/runtime";
import type { ApiInfo, SystemContainers, SystemUpdate } from "@/queries/system";

interface SystemDetailsDialogProps {
    info: ApiInfo;
    containers: SystemContainers | undefined;
    update: SystemUpdate | undefined;
}

function Detail({
    icon: Icon,
    label,
    value,
    mono = false,
}: {
    icon: LucideIcon;
    label: string;
    value: string;
    mono?: boolean;
}) {
    return (
        <div className="flex items-start gap-3">
            <Icon className="mt-0.5 size-4 text-muted-foreground" />
            <div className="min-w-0">
                <dt className="text-xs text-muted-foreground">{label}</dt>
                <dd
                    className={
                        mono ? "truncate font-mono text-xs" : "font-medium"
                    }
                    title={mono ? value : undefined}
                >
                    {value}
                </dd>
            </div>
        </div>
    );
}

export function SystemDetailsDialog({
    info,
    containers,
    update,
}: SystemDetailsDialogProps) {
    return (
        <Dialog>
            <DialogTrigger render={<Button variant="outline" size="sm" />}>
                {m.settings_system_view_details()}
            </DialogTrigger>
            <DialogContent className="max-h-11/12 overflow-y-auto sm:max-w-2xl">
                <DialogHeader>
                    <DialogTitle className="flex items-center gap-2">
                        <BoxesIcon className="size-4" />
                        {m.settings_system_details()}
                    </DialogTitle>
                    <DialogDescription>
                        {m.settings_system_details_description()}
                    </DialogDescription>
                </DialogHeader>

                <section className="grid gap-3">
                    <dl className="grid gap-2 sm:grid-cols-3">
                        <Detail
                            icon={BoxIcon}
                            label={m.settings_api_version()}
                            value={info.build.version}
                        />
                        <Detail
                            icon={ServerIcon}
                            label={m.settings_built()}
                            value={formatDateTime(
                                info.build.time ?? null,
                                getLocale(),
                                m.settings_unknown(),
                            )}
                        />
                        <Detail
                            icon={GitCommitHorizontalIcon}
                            label={m.settings_commit()}
                            value={info.build.commit}
                            mono
                        />
                    </dl>
                </section>

                {update && (
                    <>
                        <Separator />
                        <section className="grid gap-3">
                            <h3 className="flex items-center gap-2 font-medium">
                                <HardDriveIcon className="size-4 text-muted-foreground" />
                                {m.settings_system_slots()}
                            </h3>
                            <dl className="grid gap-2 sm:grid-cols-3">
                                <Detail
                                    icon={CuboidIcon}
                                    label={m.settings_system_active_slot()}
                                    value={update.update.active}
                                />
                                <Detail
                                    icon={UndoDotIcon}
                                    label={m.settings_system_other_slot()}
                                    value={update.update.other}
                                />
                                <Detail
                                    icon={HeartPulseIcon}
                                    label={m.settings_system_monitor()}
                                    value={update.monitor.phase}
                                />
                            </dl>
                            {update.monitor.detail && (
                                <p className="text-sm text-muted-foreground">
                                    {update.monitor.detail}
                                </p>
                            )}
                        </section>
                    </>
                )}

                <Separator />
                <section className="grid gap-3">
                    <div className="flex items-center justify-between gap-3">
                        <h3 className="flex items-center gap-2 font-medium">
                            <ContainerIcon className="size-4 text-muted-foreground" />
                            {m.settings_system_containers()}
                        </h3>
                        <Badge variant="secondary">
                            {containers?.count ?? 0}
                        </Badge>
                    </div>
                    {containers?.items.length ? (
                        <div className="grid gap-2">
                            {containers.items.map((container) => (
                                <div
                                    key={`${container.name}-${container.imageId}`}
                                    className="flex flex-row justify-between items-center gap-2 rounded-lg border p-3"
                                >
                                    <div>
                                        <p className="font-medium">
                                            {container.name}
                                        </p>
                                        <p
                                            className="truncate font-mono text-xs text-muted-foreground"
                                            title={container.image}
                                        >
                                            {container.image}
                                        </p>
                                    </div>
                                    <Badge variant="outline">
                                        {container.version ||
                                            m.settings_unknown()}
                                    </Badge>
                                </div>
                            ))}
                        </div>
                    ) : (
                        <p className="text-sm text-muted-foreground">
                            {m.settings_system_no_containers()}
                        </p>
                    )}
                </section>
            </DialogContent>
        </Dialog>
    );
}
