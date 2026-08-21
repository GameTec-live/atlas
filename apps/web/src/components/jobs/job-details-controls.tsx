import { Link } from "@tanstack/react-router";
import { ArrowLeftIcon, SaveIcon } from "lucide-react";
import { AddressSearch } from "@/components/address-search";
import { DueDatePicker } from "@/components/jobs/due-date-picker";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Spinner } from "@/components/ui/spinner";
import type { Coordinates } from "@/lib/route";
import { m } from "@/paraglide/messages";

type JobDetailsControlsProps = {
    from: string;
    to: string;
    destination: string;
    dueDate: Date;
    locale: string;
    isUnassigned: boolean;
    canSave: boolean;
    isSaving: boolean;
    onDestinationChange: (value: string) => void;
    onDestinationSelect: (address: string, coordinates: Coordinates) => void;
    onDueDateChange: (value: Date) => void;
    onSave: () => void;
};

export function JobDetailsControls({
    from,
    to,
    destination,
    dueDate,
    locale,
    isUnassigned,
    canSave,
    isSaving,
    onDestinationChange,
    onDestinationSelect,
    onDueDateChange,
    onSave,
}: JobDetailsControlsProps) {
    return (
        <div className="pointer-events-none absolute inset-x-0 top-0 z-10 flex items-start gap-2 p-3 sm:p-4 mr-4">
            <Button
                variant="outline"
                size="icon-lg"
                className="pointer-events-auto shrink-0 bg-background/50 shadow-md backdrop-blur"
                nativeButton={false}
                render={<Link to="/jobs" />}
                aria-label={m.job_details_back()}
                title={m.job_details_back()}
            >
                <ArrowLeftIcon />
            </Button>

            <Card className="pointer-events-auto w-full max-w-xl gap-0  py-0 bg-background/50 shadow-md backdrop-blur overflow-hidden">
                <div className="flex min-h-12 items-stretch">
                    <LocationValue label={m.jobs_from()} value={from} />
                    <DueDatePicker
                        value={dueDate}
                        disabled={!isUnassigned}
                        locale={locale}
                        onChange={onDueDateChange}
                    />
                </div>

                <div className="flex items-center border-t">
                    {isUnassigned ? (
                        <div className="relative min-w-0 flex-1">
                            <span className="pointer-events-none absolute top-1/2 left-3 z-10 -translate-y-1/2 text-sm text-muted-foreground">
                                {m.jobs_to()}
                            </span>
                            <AddressSearch
                                value={destination}
                                onValueChange={onDestinationChange}
                                onAddressSelect={onDestinationSelect}
                                placeholder={m.job_details_destination_placeholder()}
                                aria-label={m.jobs_to()}
                                className="h-12 w-full rounded-none border-0 bg-transparent pl-12 text-sm shadow-none focus-visible:ring-0 sm:text-base"
                            />
                        </div>
                    ) : (
                        <LocationValue label={m.jobs_to()} value={to} />
                    )}

                    {isUnassigned && (
                        <Button
                            variant="secondary"
                            className="mr-2 shrink-0"
                            disabled={!canSave}
                            onClick={onSave}
                            aria-label={m.job_details_save_changes()}
                        >
                            {isSaving ? (
                                <Spinner />
                            ) : (
                                <SaveIcon data-icon="inline-start" />
                            )}
                            <span className="hidden sm:inline">
                                {isSaving
                                    ? m.job_details_saving_changes()
                                    : m.job_details_save_changes()}
                            </span>
                        </Button>
                    )}
                </div>
            </Card>
        </div>
    );
}

function LocationValue({ label, value }: { label: string; value: string }) {
    return (
        <div className="flex h-12 min-w-0 flex-1 items-center gap-2 px-3">
            <span className="shrink-0 text-sm text-muted-foreground">
                {label}
            </span>
            <p className="min-w-0 truncate text-sm font-medium sm:text-base">
                {value}
            </p>
        </div>
    );
}
