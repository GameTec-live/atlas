import { useSuspenseQuery } from "@tanstack/react-query";
import { RouteIcon } from "lucide-react";
import { DashboardCardBoundary } from "@/components/dashboard/dashboard-card-boundary";
import { DashboardCardHeader } from "@/components/dashboard/dashboard-card-header";
import { EmptyJobs } from "@/components/jobs/empty-jobs";
import { JobCard } from "@/components/jobs/job-card";
import { NewJobButton } from "@/components/new-job-button";
import { Card, CardContent, CardFooter } from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";
import { getUnassignedJobs } from "@/lib/jobs";
import { m } from "@/paraglide/messages";
import { type Job, jobsQueryOptions } from "@/queries/jobs";

const cardClassName = "min-h-112 xl:col-span-4 xl:min-h-0";

export function UnassignedJobsCard({
    onDelete,
}: {
    onDelete: (job: Job) => void;
}) {
    return (
        <Card className={cardClassName}>
            <DashboardCardHeader
                title={m.jobs_unassigned_title()}
                to="/jobs"
                icon={<RouteIcon />}
            />
            <DashboardCardBoundary>
                <UnassignedJobsCardContent onDelete={onDelete} />
            </DashboardCardBoundary>
            <CardFooter className="justify-end bg-background">
                <NewJobButton />
            </CardFooter>
        </Card>
    );
}

function UnassignedJobsCardContent({
    onDelete,
}: {
    onDelete: (job: Job) => void;
}) {
    const { data: jobs } = useSuspenseQuery(jobsQueryOptions());
    const unassignedJobs = getUnassignedJobs(jobs);

    return (
        <CardContent className="min-h-0 flex-1">
            <ScrollArea className="h-full max-h-80 pr-3 xl:max-h-none">
                <div className="space-y-3">
                    {unassignedJobs.length === 0 ? (
                        <EmptyJobs title={m.jobs_empty_unassigned()} />
                    ) : (
                        unassignedJobs.map((job) => (
                            <JobCard
                                key={job.id}
                                job={job}
                                onDelete={onDelete}
                            />
                        ))
                    )}
                </div>
            </ScrollArea>
        </CardContent>
    );
}
