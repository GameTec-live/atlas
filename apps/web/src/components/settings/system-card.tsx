import { useQuery } from "@tanstack/react-query";
import { BoxIcon, GitCommitHorizontalIcon, ServerIcon } from "lucide-react";
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
import { apiInfoQueryOptions } from "@/queries/settings";

export function SystemCard() {
    const infoQuery = useQuery(apiInfoQueryOptions());

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
                {infoQuery.isPending ? (
                    <div className="grid gap-4">
                        <Skeleton className="h-12" />
                        <Skeleton className="h-12" />
                        <Skeleton className="h-12" />
                    </div>
                ) : infoQuery.isError || !infoQuery.data ? (
                    <p className="text-sm text-destructive">
                        {m.settings_system_error()}
                    </p>
                ) : (
                    <dl className="grid gap-4">
                        <div className="flex items-start gap-3">
                            <BoxIcon className="mt-0.5 size-4 text-muted-foreground" />
                            <div className="min-w-0">
                                <dt className="text-xs text-muted-foreground">
                                    {m.settings_api_version()}
                                </dt>
                                <dd className="font-medium">
                                    {infoQuery.data.build.version}
                                </dd>
                            </div>
                        </div>
                        <div className="flex items-start gap-3">
                            <ServerIcon className="mt-0.5 size-4 text-muted-foreground" />
                            <div className="min-w-0">
                                <dt className="text-xs text-muted-foreground">
                                    {m.settings_built()}
                                </dt>
                                <dd className="font-medium">
                                    {formatDateTime(
                                        infoQuery.data.build.time ?? null,
                                        getLocale(),
                                        m.settings_unknown(),
                                    )}
                                </dd>
                            </div>
                        </div>
                        <div className="flex items-start gap-3">
                            <GitCommitHorizontalIcon className="mt-0.5 size-4 text-muted-foreground" />
                            <div className="min-w-0">
                                <dt className="text-xs text-muted-foreground">
                                    {m.settings_commit()}
                                </dt>
                                <dd
                                    className="truncate font-mono text-xs"
                                    title={infoQuery.data.build.commit}
                                >
                                    {infoQuery.data.build.commit}
                                </dd>
                            </div>
                        </div>
                    </dl>
                )}
            </CardContent>
        </Card>
    );
}
