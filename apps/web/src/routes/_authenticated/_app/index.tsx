import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { DistanceChartCard } from "@/components/dashboard/distance-chart-card";
import { DriversCard } from "@/components/dashboard/drivers-card";
import { MaintenanceCard } from "@/components/dashboard/maintenance-card";
import { MiniMapCard } from "@/components/dashboard/mini-map-card";
import { UnassignedJobsCard } from "@/components/dashboard/unassigned-jobs-card";
import { DeleteJobDialog } from "@/components/jobs/delete-job-dialog";
import { useDeleteJob } from "@/hooks/use-delete-job";
import { fleetQueryOptions } from "@/queries/fleet";
import { type Job, jobsQueryOptions } from "@/queries/jobs";
import { logbooksQueryOptions } from "@/queries/logbooks";

export const Route = createFileRoute("/_authenticated/_app/")({
    loader: ({ context }) => {
        void context.queryClient.prefetchQuery(jobsQueryOptions());
        void context.queryClient.prefetchQuery(fleetQueryOptions());
        void context.queryClient.prefetchQuery(logbooksQueryOptions());
    },
    component: Dashboard,
});

function Dashboard() {
    const [jobToDelete, setJobToDelete] = useState<Job | null>(null);
    const deleteJob = useDeleteJob(() => setJobToDelete(null));

    return (
        <main className="h-full overflow-y-auto bg-muted/20 xl:overflow-hidden">
            <div className="h-full w-full p-4">
                <div className="grid min-h-full grid-cols-1 gap-4 md:grid-cols-2 xl:h-full xl:min-h-0 xl:grid-cols-12 xl:grid-rows-[minmax(0,5fr)_minmax(0,7fr)]">
                    <DriversCard />
                    <MaintenanceCard />
                    <MiniMapCard />
                    <DistanceChartCard />
                    <UnassignedJobsCard onDelete={setJobToDelete} />
                </div>
            </div>

            <DeleteJobDialog
                job={jobToDelete}
                isPending={deleteJob.isPending}
                onClose={() => setJobToDelete(null)}
                onConfirm={(job) => deleteJob.mutate(job.id)}
            />
        </main>
    );
}
