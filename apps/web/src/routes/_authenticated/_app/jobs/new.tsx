import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { JobAssignmentPanel } from "@/components/jobs/job-assignment-panel";
import { JobDetailsControls } from "@/components/jobs/job-details-controls";
import { JobDetailsMap } from "@/components/jobs/job-details-map";
import { toast } from "@/components/ui/toast";
import { api, unwrapEden } from "@/lib/api-client";
import type { Coordinates } from "@/lib/route";
import { m } from "@/paraglide/messages";
import { getLocale } from "@/paraglide/runtime";
import { jobCandidatesQueryOptions, jobsQueryKey } from "@/queries/jobs";

export const Route = createFileRoute("/_authenticated/_app/jobs/new")({
    component: NewJobPage,
});

type Location = {
    address: string;
    coordinates: Coordinates | null;
};

function NewJobPage() {
    const queryClient = useQueryClient();
    const navigate = useNavigate();
    const locale = getLocale();
    const [origin, setOrigin] = useState<Location>({
        address: "",
        coordinates: null,
    });
    const [destination, setDestination] = useState<Location>({
        address: "",
        coordinates: null,
    });
    const [dueDate, setDueDate] = useState(() => new Date());
    const [selectedDriverId, setSelectedDriverId] = useState<string>();
    const [mobileCandidatesOpen, setMobileCandidatesOpen] = useState(false);

    const originIsValid = origin.coordinates !== null;
    const destinationIsValid =
        destination.coordinates !== null || destination.address.trim() === "";
    const candidatesQuery = useQuery({
        ...jobCandidatesQueryOptions({
            from: origin.coordinates,
            to: destination.coordinates,
            dueDate,
        }),
        enabled: originIsValid && destinationIsValid,
    });
    const candidates = candidatesQuery.data ?? [];
    const selectedCandidate = candidates.some(
        (candidate) => candidate.driverId === selectedDriverId,
    )
        ? selectedDriverId
        : undefined;
    const activeDriverId = selectedCandidate ?? candidates[0]?.driverId;

    const createMutation = useMutation({
        mutationFn: (assignedDriverId: string | null) => {
            if (!origin.coordinates) throw new Error("An origin is required");

            return unwrapEden(
                api.jobs.create.post({
                    from: origin.coordinates,
                    dueDate,
                    assignedDriverId,
                    ...(destination.coordinates
                        ? { to: destination.coordinates }
                        : {}),
                }),
            );
        },
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: jobsQueryKey });
            toast.add({
                id: "job-create",
                type: "success",
                title: m.job_create_success(),
            });
            await navigate({ to: "/jobs" });
        },
        onError: () => {
            toast.add({
                id: "job-create",
                type: "error",
                title: m.job_create_error(),
                priority: "high",
            });
        },
    });

    const locationsAreValid = originIsValid && destinationIsValid;
    const create = (assignedDriverId: string | null) => {
        if (locationsAreValid && !createMutation.isPending) {
            createMutation.mutate(assignedDriverId);
        }
    };
    const assigning =
        createMutation.isPending && createMutation.variables !== null;
    const creatingUnassigned =
        createMutation.isPending && createMutation.variables === null;

    return (
        <main className="relative grid h-full min-h-0 bg-muted/30 md:grid-cols-[minmax(0,1fr)_22rem]">
            <section className="relative min-h-0">
                <JobDetailsMap
                    from={origin.coordinates}
                    to={destination.coordinates}
                />
                <JobDetailsControls
                    from=""
                    to=""
                    origin={origin.address}
                    destination={destination.address}
                    dueDate={dueDate}
                    locale={locale}
                    editable
                    originEditable
                    onOriginChange={(address) =>
                        setOrigin({ address, coordinates: null })
                    }
                    onOriginSelect={(address, coordinates) =>
                        setOrigin({ address, coordinates })
                    }
                    onDestinationChange={(address) =>
                        setDestination({ address, coordinates: null })
                    }
                    onDestinationSelect={(address, coordinates) =>
                        setDestination({ address, coordinates })
                    }
                    onDueDateChange={setDueDate}
                />
            </section>

            <JobAssignmentPanel
                candidates={candidates}
                activeDriverId={activeDriverId}
                locale={locale}
                isError={candidatesQuery.isError}
                isFetching={candidatesQuery.isFetching}
                originIsValid={originIsValid}
                destinationIsValid={destinationIsValid}
                isPending={assigning}
                disabled={
                    !activeDriverId ||
                    !locationsAreValid ||
                    createMutation.isPending
                }
                mobileOpen={mobileCandidatesOpen}
                onMobileOpenChange={setMobileCandidatesOpen}
                onSelect={setSelectedDriverId}
                onAssign={() => activeDriverId && create(activeDriverId)}
                createUnassignedAction={{
                    disabled: !locationsAreValid || createMutation.isPending,
                    isPending: creatingUnassigned,
                    onCreate: () => create(null),
                }}
            />
        </main>
    );
}
