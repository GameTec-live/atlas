import { CheckIcon, Clock3Icon, UsersIcon } from "lucide-react";
import { AllDriversList } from "@/components/jobs/all-drivers-list";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Card, CardContent } from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Skeleton } from "@/components/ui/skeleton";
import { Spinner } from "@/components/ui/spinner";
import { formatTime } from "@/lib/date";
import { cn } from "@/lib/utils";
import { m } from "@/paraglide/messages";
import type { JobCandidate } from "@/queries/jobs";
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from "../ui/empty";

type JobCandidateListProps = {
    candidates: JobCandidate[];
    activeDriverId?: string;
    locale: string;
    isError: boolean;
    isFetching: boolean;
    onSelect: (driverId: string) => void;
};

export function JobCandidateList({
    candidates,
    activeDriverId,
    locale,
    isError,
    isFetching,
    onSelect,
}: JobCandidateListProps) {
    return (
        <ScrollArea className="h-full">
            <div className="p-4">
                {isError ? (
                    <Alert variant="destructive">
                        <AlertDescription>
                            {m.job_details_candidates_error()}
                        </AlertDescription>
                    </Alert>
                ) : isFetching && candidates.length === 0 ? (
                    <div className="space-y-3">
                        {[0, 1, 2].map((index) => (
                            <Skeleton key={index} className="h-32 rounded-xl" />
                        ))}
                    </div>
                ) : candidates.length === 0 ? (
                    <Empty>
                        <EmptyHeader>
                            <EmptyMedia variant="icon">
                                <UsersIcon className="size-8" />
                            </EmptyMedia>
                            <EmptyTitle>
                                {m.job_details_no_candidates()}
                            </EmptyTitle>
                        </EmptyHeader>
                    </Empty>
                ) : (
                    <div className="space-y-3">
                        {isFetching && (
                            <p className="flex items-center gap-2 text-xs text-muted-foreground">
                                <Spinner />
                                {m.job_details_recalculating()}
                            </p>
                        )}
                        {candidates.map((candidate, index) => (
                            <CandidateCard
                                key={candidate.driverId}
                                candidate={candidate}
                                index={index}
                                locale={locale}
                                selected={candidate.driverId === activeDriverId}
                                onSelect={onSelect}
                            />
                        ))}
                    </div>
                )}

                <AllDriversList
                    activeDriverId={activeDriverId}
                    locale={locale}
                    onSelect={onSelect}
                />
            </div>
        </ScrollArea>
    );
}

function CandidateCard({
    candidate,
    index,
    locale,
    selected,
    onSelect,
}: {
    candidate: JobCandidate;
    index: number;
    locale: string;
    selected: boolean;
    onSelect: (driverId: string) => void;
}) {
    const approachMinutes = Math.max(
        1,
        Math.round(candidate.approachDurationSeconds / 60),
    );
    const lateMinutes = Math.ceil(candidate.lateBySeconds / 60);
    const select = () => onSelect(candidate.driverId);

    return (
        <Card
            className={cn(
                "cursor-pointer py-0 transition-all hover:ring-foreground/30",
                selected && "ring-2 ring-primary",
            )}
            role="button"
            tabIndex={0}
            aria-pressed={selected}
            onClick={select}
            onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    select();
                }
            }}
        >
            <CardContent className="space-y-3 p-4">
                <div className="flex min-w-0 items-center gap-2">
                    <span
                        className={cn(
                            "flex size-5 shrink-0 items-center justify-center rounded-full border text-[11px] font-semibold",
                            selected &&
                                "border-primary bg-primary text-primary-foreground",
                        )}
                    >
                        {selected ? <CheckIcon /> : index + 1}
                    </span>
                    <h2
                        className="truncate font-semibold"
                        title={candidate.driverName}
                    >
                        {candidate.driverName}
                    </h2>
                </div>

                <dl className="grid grid-cols-2 gap-3 text-xs">
                    <CandidateDetail
                        label={m.job_details_eta()}
                        value={formatTime(candidate.estimatedPickupAt, locale)}
                    />
                    <CandidateDetail
                        label={m.job_details_approach()}
                        value={m.job_details_minutes({
                            minutes: approachMinutes,
                        })}
                    />
                </dl>

                <div className="flex items-center justify-between gap-2 border-t pt-3 text-xs">
                    <span
                        className={cn(
                            "flex items-center gap-1.5 font-medium",
                            lateMinutes > 0
                                ? "text-destructive"
                                : "text-emerald-600 dark:text-emerald-400",
                        )}
                    >
                        <Clock3Icon className="size-3.5" />
                        {lateMinutes > 0
                            ? m.job_details_late({ minutes: lateMinutes })
                            : m.job_details_on_time()}
                    </span>
                    <span
                        className="truncate text-muted-foreground"
                        title={candidate.rankingTrace.summary}
                    >
                        {candidate.rankingTrace.summary}
                    </span>
                </div>
            </CardContent>
        </Card>
    );
}

function CandidateDetail({ label, value }: { label: string; value: string }) {
    return (
        <div>
            <dt className="text-muted-foreground">{label}</dt>
            <dd className="mt-0.5 font-medium">{value}</dd>
        </div>
    );
}
