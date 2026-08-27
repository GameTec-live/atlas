import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
    DatabaseIcon,
    DownloadIcon,
    HardDriveIcon,
    MapIcon,
} from "lucide-react";
import { useMemo, useState } from "react";
import { DeleteConfirmationDialog } from "@/components/settings/delete-confirmation-dialog";
import { InstallMapDialog } from "@/components/settings/install-map-dialog";
import { MapDatasetRow } from "@/components/settings/map-dataset-row";
import { MapJobProgress } from "@/components/settings/map-job-progress";
import { Button } from "@/components/ui/button";
import {
    Card,
    CardAction,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import {
    Empty,
    EmptyDescription,
    EmptyHeader,
    EmptyMedia,
    EmptyTitle,
} from "@/components/ui/empty";
import { Progress, ProgressLabel } from "@/components/ui/progress";
import { Skeleton } from "@/components/ui/skeleton";
import { Spinner } from "@/components/ui/spinner";
import { toast } from "@/components/ui/toast";
import { formatBytes } from "@/lib/number";
import { m } from "@/paraglide/messages";
import { getLocale } from "@/paraglide/runtime";
import {
    type GeodataDataset,
    geodataCatalogQueryOptions,
    geodataDatasetMutationOptions,
    geodataDatasetsQueryOptions,
    geodataInstallMutationOptions,
    geodataJobsQueryKey,
    geodataJobsQueryOptions,
} from "@/queries/settings";

export function MapDataCard() {
    const queryClient = useQueryClient();
    const jobsQuery = useQuery(geodataJobsQueryOptions());
    const activeJobs = jobsQuery.data?.items ?? [];
    const datasetsQuery = useQuery(
        geodataDatasetsQueryOptions(Boolean(activeJobs.length)),
    );
    const catalogQuery = useQuery(geodataCatalogQueryOptions());
    const [installOpen, setInstallOpen] = useState(false);
    const [deletingDataset, setDeletingDataset] =
        useState<GeodataDataset | null>(null);
    const installMutation = useMutation({
        ...geodataInstallMutationOptions(),
        onSuccess: async () => {
            await queryClient.invalidateQueries({
                queryKey: geodataJobsQueryKey,
            });
            setInstallOpen(false);
            toast.add({
                id: "settings-map-install-started",
                type: "success",
                title: m.settings_map_download_started(),
            });
        },
        onError: () =>
            toast.add({
                id: "settings-map-install-error",
                type: "error",
                title: m.settings_map_download_error(),
                priority: "high",
            }),
    });
    const datasetMutation = useMutation({
        ...geodataDatasetMutationOptions(),
        onSuccess: async (_, { operation }) => {
            await queryClient.invalidateQueries({
                queryKey: geodataJobsQueryKey,
            });
            setDeletingDataset(null);
            toast.add({
                id: "settings-map-operation-started",
                type: "success",
                title:
                    operation === "update"
                        ? m.settings_map_update_started()
                        : m.settings_map_delete_started(),
            });
        },
        onError: () =>
            toast.add({
                id: "settings-map-operation-error",
                type: "error",
                title: m.settings_map_operation_error(),
                priority: "high",
            }),
    });

    const catalogItems = useMemo(() => {
        const catalog = catalogQuery.data;
        if (!catalog || "error" in catalog) return [];
        const installedIds = new Set(
            datasetsQuery.data?.items.map((dataset) => dataset.id),
        );
        return catalog.items.filter((item) => !installedIds.has(item.id));
    }, [catalogQuery.data, datasetsQuery.data?.items]);
    const jobsByDataset = new Map(
        activeJobs.map((job) => [job.dataset_id, job]),
    );
    const installedIds = new Set(
        datasetsQuery.data?.items.map((dataset) => dataset.id),
    );
    const diskSpace = datasetsQuery.data?.disk_space;
    const diskUsedPercent = diskSpace?.total_bytes
        ? ((diskSpace.total_bytes - diskSpace.free_bytes) /
              diskSpace.total_bytes) *
          100
        : 0;

    return (
        <>
            <Card className="lg:col-span-8">
                <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                        <MapIcon className="size-4" />
                        {m.settings_map_data()}
                    </CardTitle>
                    <CardDescription>
                        {m.settings_map_data_description()}
                    </CardDescription>
                    <CardAction>
                        <Button
                            type="button"
                            size="sm"
                            onClick={() => setInstallOpen(true)}
                            disabled={
                                catalogQuery.isPending || !catalogItems.length
                            }
                        >
                            {catalogQuery.isPending ? (
                                <Spinner />
                            ) : (
                                <DownloadIcon />
                            )}
                            {m.settings_map_download()}
                        </Button>
                    </CardAction>
                </CardHeader>
                <CardContent className="grid gap-4">
                    {diskSpace && (
                        <Progress value={diskUsedPercent}>
                            <ProgressLabel className="flex items-center gap-2">
                                <HardDriveIcon className="size-4" />
                                {m.settings_map_storage()}
                            </ProgressLabel>
                            <span className="ml-auto text-sm text-muted-foreground tabular-nums">
                                {m.settings_map_storage_free({
                                    free: formatBytes(
                                        diskSpace.free_bytes,
                                        getLocale(),
                                    ),
                                })}
                            </span>
                        </Progress>
                    )}

                    {activeJobs
                        .filter((job) => !installedIds.has(job.dataset_id))
                        .map((job) => (
                            <div key={job.id} className="rounded-lg border p-3">
                                <MapJobProgress
                                    job={job}
                                    prefix={job.dataset_id}
                                />
                            </div>
                        ))}

                    <div>
                        <h3 className="mb-2 text-sm font-medium">
                            {m.settings_map_installed()}
                        </h3>
                        {datasetsQuery.isPending ? (
                            <div className="grid gap-2">
                                <Skeleton className="h-20" />
                                <Skeleton className="h-20" />
                            </div>
                        ) : datasetsQuery.isError || !datasetsQuery.data ? (
                            <div className="rounded-lg border p-6 text-center text-sm text-destructive">
                                {m.settings_map_error()}
                            </div>
                        ) : datasetsQuery.data.items.length ? (
                            <div className="overflow-hidden rounded-lg border">
                                {datasetsQuery.data.items.map((dataset) => (
                                    <MapDatasetRow
                                        key={dataset.id}
                                        dataset={dataset}
                                        job={jobsByDataset.get(dataset.id)}
                                        onUpdate={() =>
                                            datasetMutation.mutate({
                                                operation: "update",
                                                id: dataset.id,
                                            })
                                        }
                                        onDelete={() =>
                                            setDeletingDataset(dataset)
                                        }
                                    />
                                ))}
                            </div>
                        ) : (
                            <Empty className="border">
                                <EmptyHeader>
                                    <EmptyMedia variant="icon">
                                        <DatabaseIcon />
                                    </EmptyMedia>
                                    <EmptyTitle>
                                        {m.settings_map_empty()}
                                    </EmptyTitle>
                                    <EmptyDescription>
                                        {m.settings_map_empty_description()}
                                    </EmptyDescription>
                                </EmptyHeader>
                            </Empty>
                        )}
                    </div>
                </CardContent>
            </Card>

            {installOpen && (
                <InstallMapDialog
                    items={catalogItems}
                    isPending={installMutation.isPending}
                    onClose={() => setInstallOpen(false)}
                    onInstall={installMutation.mutate}
                />
            )}
            <DeleteConfirmationDialog
                open={deletingDataset !== null}
                title={m.settings_map_delete_title()}
                description={m.settings_map_delete_description({
                    dataset: deletingDataset?.name ?? "",
                })}
                actionLabel={m.settings_map_delete()}
                isPending={datasetMutation.isPending}
                onClose={() => setDeletingDataset(null)}
                onConfirm={() =>
                    deletingDataset &&
                    datasetMutation.mutate({
                        operation: "delete",
                        id: deletingDataset.id,
                    })
                }
            />
        </>
    );
}
