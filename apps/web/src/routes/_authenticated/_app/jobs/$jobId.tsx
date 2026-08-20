import {
    useMutation,
    useQuery,
    useQueryClient,
    useSuspenseQuery,
} from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { JobAssignmentPanel } from "@/components/jobs/job-assignment-panel";
import { JobDetailsControls } from "@/components/jobs/job-details-controls";
import { JobDetailsMap } from "@/components/jobs/job-details-map";
import { JobDetailsSkeleton } from "@/components/jobs/job-details-skeleton";
import { toast } from "@/components/ui/toast";
import { api, unwrapEden } from "@/lib/api-client";
import { formatJobLocation } from "@/lib/jobs";
import type { Coordinates } from "@/lib/route";
import { cn } from "@/lib/utils";
import { m } from "@/paraglide/messages";
import { getLocale } from "@/paraglide/runtime";
import {
    jobCandidatesQueryOptions,
    jobQueryKey,
    jobQueryOptions,
    jobsQueryKey,
} from "@/queries/jobs";

export const Route = createFileRoute("/_authenticated/_app/jobs/$jobId")({
    loader: ({ context, params }) =>
        context.queryClient.ensureQueryData(jobQueryOptions(params.jobId)),
    pendingComponent: JobDetailsSkeleton,
    component: JobDetailsPage,
});

type Destination = {
    address: string;
    coordinates: Coordinates | null;
};

function JobDetailsPage() {
    const { jobId } = Route.useParams();
    const { data: job } = useSuspenseQuery(jobQueryOptions(jobId));
    const queryClient = useQueryClient();
    const navigate = useNavigate();
    const locale = getLocale();
    const isUnassigned = job.assignedDriverId === null;

    const [destination, setDestination] = useState<Destination>({
        address: formatJobLocation(job.toAddress, job.to, ""),
        coordinates: job.to,
    });
    const [destinationDirty, setDestinationDirty] = useState(false);
    const [dueDate, setDueDate] = useState(job.dueDate);
    const [selectedDriverId, setSelectedDriverId] = useState<string>();
    const [mobileCandidatesOpen, setMobileCandidatesOpen] = useState(false);

    const destinationIsValid =
        !destinationDirty || destination.coordinates !== null;
    const hasUnsavedChanges =
        destinationDirty || dueDate.getTime() !== job.dueDate.getTime();

    const candidatesQuery = useQuery({
        ...jobCandidatesQueryOptions({
            from: job.from,
            to: destination.coordinates,
            dueDate,
        }),
        enabled: isUnassigned && destinationIsValid,
    });
    const candidates = candidatesQuery.data ?? [];
    const activeDriverId = selectedDriverId ?? candidates[0]?.driverId;

    const invalidateJob = () =>
        Promise.all([
            queryClient.invalidateQueries({ queryKey: jobsQueryKey }),
            queryClient.invalidateQueries({ queryKey: jobQueryKey(jobId) }),
        ]);

    const jobChanges = {
        dueDate,
        ...(destination.coordinates ? { to: destination.coordinates } : {}),
    };

    const saveMutation = useMutation({
        mutationFn: () => unwrapEden(api.jobs({ id: jobId }).put(jobChanges)),
        onSuccess: async () => {
            setDestinationDirty(false);
            await invalidateJob();
            toast.add({
                id: "job-save",
                type: "success",
                title: m.job_details_save_success(),
            });
        },
        onError: () => {
            toast.add({
                id: "job-save",
                type: "error",
                title: m.job_details_save_error(),
                priority: "high",
            });
        },
    });

    const assignMutation = useMutation({
        mutationFn: (driverId: string) =>
            unwrapEden(
                api.jobs({ id: jobId }).assign.post({
                    assignedDriverId: driverId,
                    ...jobChanges,
                }),
            ),
        onSuccess: async () => {
            await invalidateJob();
            toast.add({
                id: "job-assign",
                type: "success",
                title: m.job_details_assign_success(),
            });
            await navigate({ to: "/jobs" });
        },
        onError: () => {
            toast.add({
                id: "job-assign",
                type: "error",
                title: m.job_details_assign_error(),
                priority: "high",
            });
        },
    });

    const mutationPending = saveMutation.isPending || assignMutation.isPending;
    const assign = () => {
        if (activeDriverId && destinationIsValid) {
            assignMutation.mutate(activeDriverId);
        }
    };

    return (
        <main
            className={cn(
                "relative grid h-full min-h-0 bg-muted/30",
                isUnassigned && "md:grid-cols-[minmax(0,1fr)_22rem]",
            )}
        >
            <section className="relative min-h-0 ">
                <JobDetailsMap from={job.from} to={destination.coordinates} />
                <JobDetailsControls
                    from={formatJobLocation(
                        job.fromAddress,
                        job.from,
                        m.jobs_not_available(),
                    )}
                    to={formatJobLocation(
                        job.toAddress,
                        job.to,
                        m.jobs_not_available(),
                    )}
                    destination={destination.address}
                    dueDate={dueDate}
                    locale={locale}
                    isUnassigned={isUnassigned}
                    canSave={
                        hasUnsavedChanges &&
                        destinationIsValid &&
                        !mutationPending
                    }
                    isSaving={saveMutation.isPending}
                    onDestinationChange={(address) => {
                        setDestination({ address, coordinates: null });
                        setDestinationDirty(true);
                    }}
                    onDestinationSelect={(address, coordinates) => {
                        setDestination({ address, coordinates });
                        setDestinationDirty(true);
                    }}
                    onDueDateChange={setDueDate}
                    onSave={() => saveMutation.mutate()}
                />
            </section>

            {isUnassigned && (
                <JobAssignmentPanel
                    candidates={candidates}
                    activeDriverId={activeDriverId}
                    locale={locale}
                    isError={candidatesQuery.isError}
                    isFetching={candidatesQuery.isFetching}
                    destinationIsValid={destinationIsValid}
                    isPending={assignMutation.isPending}
                    disabled={
                        !activeDriverId ||
                        !destinationIsValid ||
                        mutationPending
                    }
                    mobileOpen={mobileCandidatesOpen}
                    onMobileOpenChange={setMobileCandidatesOpen}
                    onSelect={setSelectedDriverId}
                    onAssign={assign}
                />
            )}
        </main>
    );
}
