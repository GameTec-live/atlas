import { RefreshCwIcon, Trash2Icon } from "lucide-react";
import { MapJobProgress } from "@/components/settings/map-job-progress";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { formatDate } from "@/lib/date";
import { formatBytes } from "@/lib/number";
import { m } from "@/paraglide/messages";
import { getLocale } from "@/paraglide/runtime";
import type { GeodataDataset, GeodataJob } from "@/queries/settings";

export function MapDatasetRow({
    dataset,
    job,
    onUpdate,
    onDelete,
}: {
    dataset: GeodataDataset;
    job: GeodataJob | undefined;
    onUpdate: () => void;
    onDelete: () => void;
}) {
    return (
        <div className="grid gap-3 border-b p-4 last:border-b-0">
            <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                    <div className="flex items-center gap-2">
                        <p className="truncate font-medium">{dataset.name}</p>
                        <Badge
                            variant={
                                dataset.state === "ready"
                                    ? "secondary"
                                    : "destructive"
                            }
                        >
                            {dataset.state === "ready"
                                ? m.settings_map_ready()
                                : m.settings_map_degraded()}
                        </Badge>
                    </div>
                    <p className="mt-1 text-xs text-muted-foreground">
                        {formatBytes(dataset.size_bytes.total, getLocale())} ·{" "}
                        {m.settings_map_installed_on({
                            date: formatDate(
                                new Date(dataset.installed_at),
                                getLocale(),
                            ),
                        })}
                    </p>
                </div>
                <div className="flex gap-1">
                    <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        disabled={Boolean(job)}
                        onClick={onUpdate}
                    >
                        <RefreshCwIcon />
                    </Button>
                    <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        className="text-destructive hover:text-destructive"
                        aria-label={m.settings_map_delete()}
                        title={m.settings_map_delete()}
                        disabled={Boolean(job)}
                        onClick={onDelete}
                    >
                        <Trash2Icon />
                    </Button>
                </div>
            </div>
            {job && <MapJobProgress job={job} />}
        </div>
    );
}
