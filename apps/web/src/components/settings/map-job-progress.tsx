import { CancelMapJobButton } from "@/components/settings/cancel-map-job-button";
import { Badge } from "@/components/ui/badge";
import { Progress, ProgressLabel } from "@/components/ui/progress";
import { m } from "@/paraglide/messages";
import type { GeodataJob } from "@/queries/settings";

export function MapJobProgress({
    job,
    prefix,
}: {
    job: GeodataJob;
    prefix?: string;
}) {
    const operation = {
        install: m.settings_map_installing(),
        update: m.settings_map_updating(),
        delete: m.settings_map_deleting(),
    }[job.operation];

    return (
        <Progress value={job.progress * 100}>
            <ProgressLabel className="capitalize flex items-center gap-2">
                {prefix && prefix}
                <Badge variant="outline">
                    {job.stage.replaceAll("_", " ")}
                </Badge>
                <Badge
                    variant={
                        job.operation === "install"
                            ? "default"
                            : job.operation === "update"
                              ? "secondary"
                              : "destructive"
                    }
                >
                    {operation}
                </Badge>
            </ProgressLabel>
            <div className="ml-auto flex items-center gap-2">
                <span className="text-sm text-muted-foreground tabular-nums">
                    {Math.round(job.progress * 100)}%
                </span>
                <CancelMapJobButton jobId={job.id} />
            </div>
        </Progress>
    );
}
