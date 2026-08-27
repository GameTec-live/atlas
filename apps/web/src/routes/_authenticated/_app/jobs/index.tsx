import { useSuspenseQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { useMemo, useState } from "react";
import { DeleteJobDialog } from "@/components/jobs/delete-job-dialog";
import { JobHistorySection } from "@/components/jobs/job-history-section";
import { JobsPageSkeleton } from "@/components/jobs/jobs-page-skeleton";
import { UnassignedJobsSection } from "@/components/jobs/unassigned-jobs-section";
import { useDeleteJob } from "@/hooks/use-delete-job";
import { getAssignedJobs, getUnassignedJobs } from "@/lib/jobs";
import { downloadJobsCsv, type JobCsvLabels } from "@/lib/jobs-csv";
import { m } from "@/paraglide/messages";
import { getLocale } from "@/paraglide/runtime";
import { type Job, jobsQueryOptions } from "@/queries/jobs";

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
    const [jobToDelete, setJobToDelete] = useState<Job | null>(null);

    const assignedJobs = useMemo(() => getAssignedJobs(jobs), [jobs]);
    const unassignedJobs = useMemo(() => getUnassignedJobs(jobs), [jobs]);
    const deleteJob = useDeleteJob(() => setJobToDelete(null));

    return (
        <main
            className="flex h-full min-h-0 flex-col overflow-hidden lg:flex-row"
            data-dashboard-transition-target
        >
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
                isPending={deleteJob.isPending}
                onClose={() => setJobToDelete(null)}
                onConfirm={(job) => deleteJob.mutate(job.id)}
            />
        </main>
    );
}
