import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { ExternalLinkIcon } from "lucide-react";
import { EmptyJobs } from "@/components/jobs/empty-jobs";
import { JobCard } from "@/components/jobs/job-card";
import { JobsSectionHeader } from "@/components/jobs/jobs-section-header";
import { NewJobButton } from "@/components/new-job-button";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Spinner } from "@/components/ui/spinner";
import { m } from "@/paraglide/messages";
import { type Job, jobTokenQueryOptions } from "@/queries/jobs";

export function UnassignedJobsSection({
    jobs,
    onDelete,
    onDownload,
}: {
    jobs: Job[];
    onDelete: (job: Job) => void;
    onDownload: () => void;
}) {
    const tokenQuery = useQuery(jobTokenQueryOptions());

    return (
        <aside className="flex min-h-128 w-full shrink-0 flex-col border-t bg-muted/20 lg:w-96 lg:border-t-0 lg:border-l">
            <JobsSectionHeader
                title={m.jobs_unassigned_title()}
                downloadLabel={m.jobs_download_unassigned()}
                onDownload={onDownload}
                action={
                    tokenQuery.data ? (
                        <Button
                            variant="ghost"
                            size="icon"
                            render={
                                <Link
                                    to="/jobs/public"
                                    search={{
                                        jobtoken: tokenQuery.data.jobtoken,
                                    }}
                                    target="_blank"
                                    rel="noreferrer"
                                />
                            }
                            aria-label={m.jobs_public_link_label()}
                            title={m.jobs_public_link_label()}
                        >
                            <ExternalLinkIcon />
                        </Button>
                    ) : (
                        <Button
                            variant="ghost"
                            size="icon"
                            disabled
                            aria-label={m.jobs_public_link_label()}
                            title={
                                tokenQuery.isError
                                    ? m.jobs_public_link_error()
                                    : m.jobs_public_link_label()
                            }
                        >
                            {tokenQuery.isPending ? (
                                <Spinner />
                            ) : (
                                <ExternalLinkIcon />
                            )}
                        </Button>
                    )
                }
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
