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
    origin?: string;
    destination: string;
    dueDate: Date;
    locale: string;
    editable: boolean;
    originEditable?: boolean;
    saveAction?: {
        disabled: boolean;
        isPending: boolean;
        onSave: () => void;
    };
    onOriginChange?: (value: string) => void;
    onOriginSelect?: (address: string, coordinates: Coordinates) => void;
    onDestinationChange: (value: string) => void;
    onDestinationSelect: (address: string, coordinates: Coordinates) => void;
    onDueDateChange: (value: Date) => void;
};

export function JobDetailsControls({
    from,
    to,
    origin = from,
    destination,
    dueDate,
    locale,
    editable,
    originEditable = false,
    saveAction,
    onOriginChange,
    onOriginSelect,
    onDestinationChange,
    onDestinationSelect,
    onDueDateChange,
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
                    {originEditable && onOriginChange && onOriginSelect ? (
                        <LocationSearch
                            label={m.jobs_from()}
                            value={origin}
                            placeholder={m.job_details_origin_placeholder()}
                            onChange={onOriginChange}
                            onSelect={onOriginSelect}
                        />
                    ) : (
                        <LocationValue label={m.jobs_from()} value={from} />
                    )}
                    <DueDatePicker
                        value={dueDate}
                        disabled={!editable}
                        locale={locale}
                        onChange={onDueDateChange}
                    />
                </div>

                <div className="flex items-center border-t">
                    {editable ? (
                        <LocationSearch
                            label={m.jobs_to()}
                            value={destination}
                            placeholder={m.job_details_destination_placeholder()}
                            onChange={onDestinationChange}
                            onSelect={onDestinationSelect}
                        />
                    ) : (
                        <LocationValue label={m.jobs_to()} value={to} />
                    )}

                    {saveAction && (
                        <Button
                            variant="secondary"
                            className="mr-2 shrink-0"
                            disabled={saveAction.disabled}
                            onClick={saveAction.onSave}
                            aria-label={m.job_details_save_changes()}
                        >
                            {saveAction.isPending ? (
                                <Spinner />
                            ) : (
                                <SaveIcon data-icon="inline-start" />
                            )}
                            <span className="hidden sm:inline">
                                {saveAction.isPending
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

type LocationSearchProps = {
    label: string;
    value: string;
    placeholder: string;
    onChange: (value: string) => void;
    onSelect: (address: string, coordinates: Coordinates) => void;
};

function LocationSearch({
    label,
    value,
    placeholder,
    onChange,
    onSelect,
}: LocationSearchProps) {
    return (
        <div className="relative min-w-0 flex-1">
            <span className="pointer-events-none absolute top-1/2 left-3 z-10 -translate-y-1/2 text-sm text-muted-foreground">
                {label}
            </span>
            <AddressSearch
                value={value}
                onValueChange={onChange}
                onAddressSelect={onSelect}
                placeholder={placeholder}
                aria-label={label}
                className="h-12 w-full rounded-none border-0 bg-transparent pl-12 text-sm shadow-none focus-visible:ring-0 sm:text-base"
            />
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
