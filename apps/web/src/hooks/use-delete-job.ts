import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "@/components/ui/toast";
import { api, unwrapEden } from "@/lib/api-client";
import { m } from "@/paraglide/messages";
import { jobsQueryKey } from "@/queries/jobs";

export function useDeleteJob(onSuccess: () => void) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (id: string) => unwrapEden(api.jobs({ id }).delete()),
        onSuccess: async () => {
            onSuccess();
            await queryClient.invalidateQueries({ queryKey: jobsQueryKey });
            toast.add({
                id: "job-delete",
                type: "success",
                title: m.jobs_delete_success(),
            });
        },
        onError: () => {
            toast.add({
                id: "job-delete-error",
                type: "error",
                title: m.jobs_delete_error(),
                priority: "high",
            });
        },
    });
}
