import {
    useMutation,
    useQueryClient,
    useSuspenseQuery,
} from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { useMemo, useState } from "react";
import { DeleteJobDialog } from "@/components/jobs/delete-job-dialog";
import { JobHistorySection } from "@/components/jobs/job-history-section";
import { JobsPageSkeleton } from "@/components/jobs/jobs-page-skeleton";
import { UnassignedJobsSection } from "@/components/jobs/unassigned-jobs-section";
import { toast } from "@/components/ui/toast";
import { api, unwrapEden } from "@/lib/api-client";
import { downloadJobsCsv, type JobCsvLabels } from "@/lib/jobs-csv";
import { m } from "@/paraglide/messages";
import { getLocale } from "@/paraglide/runtime";
import { type Job, jobsQueryKey, jobsQueryOptions } from "@/queries/jobs";

export const Route = createFileRoute("/_authenticated/_app/jobs/")({
    loader: ({ context }) =>
        context.queryClient.ensureQueryData(jobsQueryOptions()),
    pendingComponent: JobsPageSkeleton,
    component: JobsPage,
});

const jobCsvLabels = {
    from: m.jobs_from(),
    to: m.jobs_to(),
    due: m.jobs_due(),
    started: m.jobs_started(),
    completed: m.jobs_completed(),
    assigned: m.jobs_assigned(),
    assignedDriverId: "Assigned Driver ID",
    vehicleId: "Vehicle ID",
    note: "Note",
    created: "Created",
    notAvailable: m.jobs_not_available(),
} satisfies JobCsvLabels;

function JobsPage() {
    const { data: jobs } = useSuspenseQuery(jobsQueryOptions());
    const queryClient = useQueryClient();
    const [jobToDelete, setJobToDelete] = useState<Job | null>(null);

    const assignedJobs = useMemo(
        () =>
            [...jobs]
                .filter((job) => job.assignedDriverId !== null)
                .sort(
                    (left, right) =>
                        right.dueDate.getTime() - left.dueDate.getTime(),
                ),
        [jobs],
    );
    const unassignedJobs = useMemo(
        () =>
            [...jobs]
                .filter((job) => job.assignedDriverId === null)
                .sort(
                    (left, right) =>
                        left.dueDate.getTime() - right.dueDate.getTime(),
                ),
        [jobs],
    );
    const deleteMutation = useMutation({
        mutationFn: (id: string) => unwrapEden(api.jobs({ id }).delete()),
        onSuccess: async () => {
            setJobToDelete(null);
            await queryClient.invalidateQueries({ queryKey: jobsQueryKey });
            toast.add({
                id: "job-delete",
                type: "success",
                title: m.jobs_delete_success(),
            });
        },
        onError: () => {
            toast.add({
                id: "job-delete",
                type: "error",
                title: m.jobs_delete_error(),
                priority: "high",
            });
        },
    });

    return (
        <main className="flex h-full min-h-0 flex-col overflow-hidden lg:flex-row">
            <JobHistorySection
                jobs={assignedJobs}
                onDownload={() =>
                    downloadJobsCsv({
                        jobs: assignedJobs,
                        filename: "job-history.csv",
                        locale: getLocale(),
                        labels: jobCsvLabels,
                    })
                }
            />

            <UnassignedJobsSection
                jobs={unassignedJobs}
                onDelete={setJobToDelete}
                onDownload={() =>
                    downloadJobsCsv({
                        jobs: unassignedJobs,
                        filename: "unassigned-jobs.csv",
                        locale: getLocale(),
                        labels: jobCsvLabels,
                    })
                }
            />

            <DeleteJobDialog
                job={jobToDelete}
                isPending={deleteMutation.isPending}
                onClose={() => setJobToDelete(null)}
                onConfirm={(job) => deleteMutation.mutate(job.id)}
            />
        </main>
    );
}
