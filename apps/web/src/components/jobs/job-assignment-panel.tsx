import { ArrowRightIcon, UsersIcon } from "lucide-react";
import { JobCandidateList } from "@/components/jobs/job-candidate-list";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
    Drawer,
    DrawerContent,
    DrawerFooter,
    DrawerHeader,
    DrawerTitle,
} from "@/components/ui/drawer";
import { Spinner } from "@/components/ui/spinner";
import { m } from "@/paraglide/messages";
import type { JobCandidate } from "@/queries/jobs";

type JobAssignmentPanelProps = {
    candidates: JobCandidate[];
    activeDriverId?: string;
    locale: string;
    isError: boolean;
    isFetching: boolean;
    destinationIsValid: boolean;
    isPending: boolean;
    disabled: boolean;
    mobileOpen: boolean;
    onMobileOpenChange: (open: boolean) => void;
    onSelect: (driverId: string) => void;
    onAssign: () => void;
};

type AssignmentActionProps = Pick<
    JobAssignmentPanelProps,
    "destinationIsValid" | "isPending" | "disabled" | "onAssign"
>;

export function JobAssignmentPanel({
    candidates,
    activeDriverId,
    locale,
    isError,
    isFetching,
    destinationIsValid,
    isPending,
    disabled,
    mobileOpen,
    onMobileOpenChange,
    onSelect,
    onAssign,
}: JobAssignmentPanelProps) {
    const candidateListProps = {
        candidates,
        activeDriverId,
        locale,
        isError,
        isFetching,
        onSelect,
    };
    const actionProps = {
        destinationIsValid,
        isPending,
        disabled,
        onAssign,
    } satisfies AssignmentActionProps;

    return (
        <>
            <Button
                size="lg"
                className="absolute right-4 bottom-4 z-10 shadow-md md:hidden"
                onClick={() => onMobileOpenChange(true)}
            >
                <UsersIcon data-icon="inline-start" />
                {m.job_details_open_candidates()}
                {candidates.length > 0 && (
                    <Badge variant="secondary" className="ml-1">
                        {candidates.length}
                    </Badge>
                )}
            </Button>

            <aside className="hidden min-h-0 flex-col border-l md:flex">
                <div className="border-b p-4">
                    <h1 className="font-heading text-lg font-semibold">
                        {m.job_details_candidates()}
                    </h1>
                </div>
                <div className="min-h-0 flex-1">
                    <JobCandidateList {...candidateListProps} />
                </div>
                <div className="border-t p-4">
                    <AssignmentAction {...actionProps} />
                </div>
            </aside>

            <Drawer
                open={mobileOpen}
                onOpenChange={onMobileOpenChange}
                showSwipeHandle
            >
                <DrawerContent>
                    <DrawerHeader>
                        <DrawerTitle>{m.job_details_candidates()}</DrawerTitle>
                    </DrawerHeader>
                    <div className="min-h-0 flex-1">
                        <JobCandidateList {...candidateListProps} />
                    </div>
                    <DrawerFooter>
                        <AssignmentAction {...actionProps} />
                    </DrawerFooter>
                </DrawerContent>
            </Drawer>
        </>
    );
}

function AssignmentAction({
    destinationIsValid,
    isPending,
    disabled,
    onAssign,
}: AssignmentActionProps) {
    return (
        <div className="space-y-3">
            {!destinationIsValid && (
                <p className="text-sm text-destructive">
                    {m.job_details_destination_required()}
                </p>
            )}
            <Button
                size="lg"
                className="w-full"
                disabled={disabled}
                onClick={onAssign}
            >
                {isPending && <Spinner />}
                {isPending ? m.job_details_assigning() : m.job_details_assign()}
                {!isPending && <ArrowRightIcon data-icon="inline-end" />}
            </Button>
        </div>
    );
}
