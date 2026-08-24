import {
    useMutation,
    useQueryClient,
    useSuspenseQuery,
} from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { NotebookTabsIcon } from "lucide-react";
import { useMemo, useState } from "react";
import { DriverLogbook } from "@/components/logbook/driver-logbook";
import { InvalidateEntryDialog } from "@/components/logbook/invalidate-entry-dialog";
import {
    Empty,
    EmptyHeader,
    EmptyMedia,
    EmptyTitle,
} from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";
import { toast } from "@/components/ui/toast";
import { api, unwrapEden } from "@/lib/api-client";
import { downloadLogbookCsv, type LogbookCsvLabels } from "@/lib/logbook-csv";
import { m } from "@/paraglide/messages";
import { getLocale } from "@/paraglide/runtime";
import {
    type LogbookEntry,
    logbooksQueryKey,
    logbooksQueryOptions,
    logbookUsersQueryOptions,
} from "@/queries/logbooks";

export const Route = createFileRoute("/_authenticated/_app/logbook/")({
    loader: ({ context }) =>
        Promise.all([
            context.queryClient.ensureQueryData(logbooksQueryOptions()),
            context.queryClient.ensureQueryData(logbookUsersQueryOptions()),
        ]),
    pendingComponent: LogbookPageSkeleton,
    component: LogbookPage,
});

function LogbookPage() {
    const { data: entries } = useSuspenseQuery(logbooksQueryOptions());
    const { data: users } = useSuspenseQuery(logbookUsersQueryOptions());
    const queryClient = useQueryClient();
    const locale = getLocale();
    const [entryToInvalidate, setEntryToInvalidate] =
        useState<LogbookEntry | null>(null);

    const groups = useMemo(() => {
        const entriesByDriver = new Map<string, LogbookEntry[]>();

        for (const entry of entries) {
            const key = entry.driverId ?? "deleted-user";
            const driverEntries = entriesByDriver.get(key);
            if (driverEntries) driverEntries.push(entry);
            else entriesByDriver.set(key, [entry]);
        }

        const userGroups = users.map((user) => ({
            id: user.id,
            name: user.name,
            entries: entriesByDriver.get(user.id) ?? [],
        }));

        const knownUserIds = new Set(users.map((user) => user.id));
        const unknownGroups = [...entriesByDriver]
            .filter(([driverId]) => !knownUserIds.has(driverId))
            .map(([driverId, driverEntries]) => ({
                id: driverId,
                name: driverEntries[0]?.driverName ?? m.logbook_deleted_user(),
                entries: driverEntries,
            }));

        return [...userGroups, ...unknownGroups].map((group) => ({
            ...group,
            entries: [...group.entries].sort(
                (left, right) =>
                    right.startedAt.getTime() - left.startedAt.getTime(),
            ),
        }));
    }, [entries, users]);

    const invalidateMutation = useMutation({
        mutationFn: (id: string) => unwrapEden(api.logbooks({ id }).delete()),
        onSuccess: async () => {
            setEntryToInvalidate(null);
            await queryClient.invalidateQueries({ queryKey: logbooksQueryKey });
            toast.add({
                id: "logbook-invalidate",
                type: "success",
                title: m.logbook_invalidate_success(),
            });
        },
        onError: () => {
            toast.add({
                id: "logbook-invalidate-error",
                type: "error",
                title: m.logbook_invalidate_error(),
                priority: "high",
            });
        },
    });

    const csvLabels = {
        date: m.logbook_date(),
        licensePlate: m.logbook_license_plate(),
        vehicle: m.logbook_vehicle(),
        kmStart: m.logbook_km_start(),
        kmEnd: m.logbook_km_end(),
        timeStart: m.logbook_time_start(),
        timeEnd: m.logbook_time_end(),
        revenue: m.logbook_revenue(),
        status: m.logbook_status(),
        valid: m.logbook_valid(),
        invalid: m.logbook_invalid(),
        notAvailable: m.logbook_not_available(),
    } satisfies LogbookCsvLabels;

    return (
        <main className="h-full overflow-y-auto">
            <div className="mx-auto flex w-full max-w-7xl flex-col gap-5 p-4 sm:p-6">
                <h1 className="font-heading text-xl font-semibold tracking-tight">
                    {m.logbook_title()}
                </h1>

                {groups.length === 0 ? (
                    <Empty className="min-h-64 border">
                        <EmptyHeader>
                            <EmptyMedia variant="icon">
                                <NotebookTabsIcon />
                            </EmptyMedia>
                            <EmptyTitle>{m.logbook_no_users()}</EmptyTitle>
                        </EmptyHeader>
                    </Empty>
                ) : (
                    <div className="space-y-3">
                        {groups.map((group) => (
                            <DriverLogbook
                                key={group.id}
                                driverName={group.name}
                                entries={group.entries}
                                locale={locale}
                                onInvalidate={setEntryToInvalidate}
                                onDownload={() =>
                                    downloadLogbookCsv({
                                        entries: group.entries,
                                        filename: `${safeFilename(group.name)}-logbook.csv`,
                                        locale,
                                        labels: csvLabels,
                                    })
                                }
                            />
                        ))}
                    </div>
                )}
            </div>

            <InvalidateEntryDialog
                entry={entryToInvalidate}
                isPending={invalidateMutation.isPending}
                onClose={() => setEntryToInvalidate(null)}
                onConfirm={(entry) => invalidateMutation.mutate(entry.id)}
            />
        </main>
    );
}

function safeFilename(value: string) {
    return (
        value
            .trim()
            .toLocaleLowerCase()
            .replace(/[^\p{L}\p{N}]+/gu, "-")
            .replace(/^-|-$/g, "") || "driver"
    );
}

function LogbookPageSkeleton() {
    return (
        <main className="h-full overflow-hidden">
            <div className="mx-auto flex w-full max-w-7xl flex-col gap-5 p-4 sm:p-6">
                <div className="space-y-2">
                    <Skeleton className="h-7 w-32" />
                    <Skeleton className="h-4 w-72 max-w-full" />
                </div>
                <div className="space-y-3">
                    <Skeleton className="h-14 w-full rounded-xl" />
                    <Skeleton className="h-14 w-full rounded-xl" />
                    <Skeleton className="h-14 w-full rounded-xl" />
                </div>
            </div>
        </main>
    );
}
