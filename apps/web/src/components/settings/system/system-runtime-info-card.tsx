import { useQuery } from "@tanstack/react-query";
import {
    BoxIcon,
    GitCommitHorizontalIcon,
    type LucideIcon,
    ServerIcon,
} from "lucide-react";
import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { formatDateTime } from "@/lib/date";
import { m } from "@/paraglide/messages";
import { getLocale } from "@/paraglide/runtime";
import { apiInfoQueryOptions } from "@/queries/system";

function RuntimeDetail({
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

export function SystemRuntimeInfoCard() {
    const info = useQuery(apiInfoQueryOptions());

    return (
        <Card className="lg:col-span-4">
            <CardHeader>
                <CardTitle className="flex items-center gap-2">
                    <ServerIcon className="size-4" />
                    {m.settings_system()}
                </CardTitle>
                <CardDescription>
                    {m.settings_system_description()}
                </CardDescription>
            </CardHeader>
            <CardContent>
                {info.isPending ? (
                    <div className="grid gap-4">
                        {[0, 1, 2].map((item) => (
                            <Skeleton key={item} className="h-12" />
                        ))}
                    </div>
                ) : info.isError || !info.data ? (
                    <p className="text-sm text-destructive">
                        {m.settings_system_error()}
                    </p>
                ) : (
                    <dl className="grid gap-4">
                        <RuntimeDetail
                            icon={BoxIcon}
                            label={m.settings_api_version()}
                            value={info.data.build.version}
                        />
                        <RuntimeDetail
                            icon={ServerIcon}
                            label={m.settings_built()}
                            value={formatDateTime(
                                info.data.build.time ?? null,
                                getLocale(),
                                m.settings_unknown(),
                            )}
                        />
                        <RuntimeDetail
                            icon={GitCommitHorizontalIcon}
                            label={m.settings_commit()}
                            value={info.data.build.commit}
                            mono
                        />
                    </dl>
                )}
            </CardContent>
        </Card>
    );
}
