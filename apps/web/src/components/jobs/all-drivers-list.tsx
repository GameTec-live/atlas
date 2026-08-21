import { useQuery } from "@tanstack/react-query";
import { CheckIcon, ChevronDownIcon } from "lucide-react";
import { useMemo, useState } from "react";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
    Collapsible,
    CollapsibleContent,
    CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { m } from "@/paraglide/messages";
import { driversQueryOptions } from "@/queries/drivers";

type AllDriversListProps = {
    activeDriverId?: string;
    locale: string;
    onSelect: (driverId: string) => void;
};

export function AllDriversList({
    activeDriverId,
    locale,
    onSelect,
}: AllDriversListProps) {
    const [open, setOpen] = useState(false);
    const driversQuery = useQuery({
        ...driversQueryOptions(),
        enabled: open,
    });
    const drivers = useMemo(
        () =>
            [...(driversQuery.data ?? [])].sort((a, b) =>
                a.name.localeCompare(b.name, locale),
            ),
        [driversQuery.data, locale],
    );

    return (
        <Collapsible
            open={open}
            onOpenChange={setOpen}
            className="mt-4 border-t pt-2"
        >
            <CollapsibleTrigger
                render={
                    <Button
                        variant="ghost"
                        className="group w-full justify-between px-2 text-muted-foreground"
                    />
                }
            >
                {m.job_details_all_drivers()}
                <ChevronDownIcon className="transition-transform group-data-panel-open:rotate-180" />
            </CollapsibleTrigger>
            <CollapsibleContent className="pt-1">
                {driversQuery.isPending ? (
                    <div className="space-y-1 py-1">
                        {[0, 1, 2].map((index) => (
                            <Skeleton key={index} className="h-8" />
                        ))}
                    </div>
                ) : driversQuery.isError ? (
                    <Alert variant="destructive" className="mt-1">
                        <AlertDescription>
                            {m.job_details_all_drivers_error()}
                        </AlertDescription>
                    </Alert>
                ) : drivers.length === 0 ? (
                    <p className="px-2 py-3 text-sm text-muted-foreground">
                        {m.job_details_no_drivers()}
                    </p>
                ) : (
                    <div className="space-y-1 py-1">
                        {drivers.map((driver) => {
                            const selected = driver.driverId === activeDriverId;

                            return (
                                <Button
                                    key={driver.driverId}
                                    variant="ghost"
                                    className={cn(
                                        "w-full justify-start font-normal",
                                        selected &&
                                            "bg-primary/10 text-primary hover:bg-primary/15 hover:text-primary",
                                    )}
                                    aria-pressed={selected}
                                    onClick={() => onSelect(driver.driverId)}
                                >
                                    <span className="flex size-4 shrink-0 items-center justify-center">
                                        {selected && <CheckIcon />}
                                    </span>
                                    <span className="truncate">
                                        {driver.name}
                                    </span>
                                </Button>
                            );
                        })}
                    </div>
                )}
            </CollapsibleContent>
        </Collapsible>
    );
}
