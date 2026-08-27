import { useMutation, useQueryClient } from "@tanstack/react-query";
import { XIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { toast } from "@/components/ui/toast";
import { m } from "@/paraglide/messages";
import {
    geodataJobCancelMutationOptions,
    geodataJobsQueryKey,
} from "@/queries/settings";

export function CancelMapJobButton({ jobId }: { jobId: string }) {
    const queryClient = useQueryClient();
    const cancelMutation = useMutation({
        ...geodataJobCancelMutationOptions(),
        onSuccess: async () => {
            await queryClient.invalidateQueries({
                queryKey: geodataJobsQueryKey,
            });
            toast.add({
                id: `settings-map-job-cancelled-${jobId}`,
                type: "success",
                title: m.settings_map_job_cancelled(),
            });
        },
        onError: () =>
            toast.add({
                id: `settings-map-job-cancel-error-${jobId}`,
                type: "error",
                title: m.settings_map_job_cancel_error(),
                priority: "high",
            }),
    });

    return (
        <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            disabled={cancelMutation.isPending}
            onClick={() => cancelMutation.mutate(jobId)}
        >
            {cancelMutation.isPending ? <Spinner /> : <XIcon />}
        </Button>
    );
}
