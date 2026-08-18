import { EmptyJobs } from "@/components/jobs/empty-jobs";
import { JobCard } from "@/components/jobs/job-card";
import { JobsSectionHeader } from "@/components/jobs/jobs-section-header";
import { NewJobButton } from "@/components/new-job-button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { m } from "@/paraglide/messages";
import type { Job } from "@/queries/jobs";

export function UnassignedJobsSection({
    jobs,
    onDelete,
    onDownload,
}: {
    jobs: Job[];
    onDelete: (job: Job) => void;
    onDownload: () => void;
}) {
    return (
        <aside className="flex min-h-128 w-full shrink-0 flex-col border-t bg-muted/20 lg:w-96 lg:border-t-0 lg:border-l">
            <JobsSectionHeader
                title={m.jobs_unassigned_title()}
                downloadLabel={m.jobs_download_unassigned()}
                onDownload={onDownload}
            />
            <ScrollArea className="min-h-0 flex-1">
                <div className="space-y-3 p-4">
                    {jobs.length === 0 ? (
                        <EmptyJobs title={m.jobs_empty_unassigned()} />
                    ) : (
                        jobs.map((job) => (
                            <JobCard
                                key={job.id}
                                job={job}
                                onDelete={onDelete}
                            />
                        ))
                    )}
                </div>
            </ScrollArea>
            <div className="flex shrink-0 justify-end border-t bg-background p-4">
                <NewJobButton />
            </div>
        </aside>
    );
}
