import {
    ChevronDownIcon,
    CircleSlash2Icon,
    DownloadIcon,
    NotebookTabsIcon,
} from "lucide-react";
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
    Collapsible,
    CollapsibleContent,
    CollapsibleTrigger,
} from "@/components/ui/collapsible";
import {
    Empty,
    EmptyHeader,
    EmptyMedia,
    EmptyTitle,
} from "@/components/ui/empty";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
import { formatDate, formatTime } from "@/lib/date";
import { formatLogbookVehicle } from "@/lib/logbook-csv";
import { formatNumber } from "@/lib/number";
import { m } from "@/paraglide/messages";
import type { LogbookEntry } from "@/queries/logbooks";

export function DriverLogbook({
    driverName,
    entries,
    locale,
    onDownload,
    onInvalidate,
}: {
    driverName: string;
    entries: LogbookEntry[];
    locale: string;
    onDownload: () => void;
    onInvalidate: (entry: LogbookEntry) => void;
}) {
    const [open, setOpen] = useState(false);

    return (
        <Collapsible
            open={open}
            onOpenChange={setOpen}
            className="overflow-hidden rounded-xl border bg-card shadow-xs"
        >
            <div className="flex min-h-14 items-stretch">
                <CollapsibleTrigger
                    aria-label={driverName}
                    className="group flex min-w-0 flex-1 items-center gap-3 px-4 py-2 hover:bg-muted focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring/50"
                >
                    <ChevronDownIcon className="size-4 shrink-0 transition-transform group-data-panel-open:rotate-180" />
                    <span className="truncate font-medium">{driverName}</span>
                    <Badge
                        aria-hidden="true"
                        variant="secondary"
                        className="ml-1"
                    >
                        {entries.length}
                    </Badge>
                </CollapsibleTrigger>
                <Button
                    variant="ghost"
                    size="icon"
                    className="h-auto w-14 self-stretch rounded-none border-l"
                    aria-label={m.logbook_download_driver({
                        driver: driverName,
                    })}
                    title={m.logbook_download_driver({ driver: driverName })}
                    onClick={onDownload}
                >
                    <DownloadIcon />
                </Button>
            </div>

            <CollapsibleContent className="border-t">
                {entries.length === 0 ? (
                    <Empty>
                        <EmptyHeader>
                            <EmptyMedia variant="icon">
                                <NotebookTabsIcon />
                            </EmptyMedia>
                            <EmptyTitle>{m.logbook_empty_driver()}</EmptyTitle>
                        </EmptyHeader>
                    </Empty>
                ) : (
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHead>{m.logbook_date()}</TableHead>
                                <TableHead>
                                    {m.logbook_license_plate()}
                                </TableHead>
                                <TableHead>{m.logbook_vehicle()}</TableHead>
                                <TableHead>{m.logbook_km_start()}</TableHead>
                                <TableHead>{m.logbook_km_end()}</TableHead>
                                <TableHead>{m.logbook_time_start()}</TableHead>
                                <TableHead>{m.logbook_time_end()}</TableHead>
                                <TableHead>{m.logbook_revenue()}</TableHead>
                                <TableHead className="w-14 text-right">
                                    <span className="sr-only">
                                        {m.logbook_actions()}
                                    </span>
                                </TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {entries.map((entry) => (
                                <TableRow
                                    key={entry.id}
                                    className={
                                        entry.invalid
                                            ? "bg-muted/60 text-muted-foreground hover:bg-muted/70"
                                            : undefined
                                    }
                                >
                                    <TableCell>
                                        {formatDate(entry.startedAt, locale)}
                                    </TableCell>
                                    <TableCell>
                                        {entry.vehicle?.licensePlate ??
                                            m.logbook_not_available()}
                                    </TableCell>
                                    <TableCell>
                                        {formatLogbookVehicle(
                                            entry,
                                            m.logbook_not_available(),
                                        )}
                                    </TableCell>
                                    <TableCell className="tabular-nums">
                                        {formatNumber(
                                            entry.startOdometer,
                                            locale,
                                        )}
                                    </TableCell>
                                    <TableCell className="tabular-nums">
                                        {formatNumber(
                                            entry.endOdometer,
                                            locale,
                                            m.logbook_not_available(),
                                        )}
                                    </TableCell>
                                    <TableCell className="tabular-nums">
                                        {formatTime(entry.startedAt, locale)}
                                    </TableCell>
                                    <TableCell className="tabular-nums">
                                        {entry.endedAt
                                            ? formatTime(entry.endedAt, locale)
                                            : m.logbook_not_available()}
                                    </TableCell>
                                    <TableCell className="tabular-nums">
                                        {entry.revenue === null
                                            ? m.logbook_not_available()
                                            : new Intl.NumberFormat(locale, {
                                                  style: "currency",
                                                  currency: "EUR",
                                              }).format(entry.revenue)}
                                    </TableCell>
                                    <TableCell className="text-right">
                                        {entry.invalid ? (
                                            <Badge variant="outline">
                                                {m.logbook_invalid()}
                                            </Badge>
                                        ) : (
                                            <Button
                                                variant="ghost"
                                                size="icon-sm"
                                                aria-label={m.logbook_mark_invalid()}
                                                title={m.logbook_mark_invalid()}
                                                onClick={() =>
                                                    onInvalidate(entry)
                                                }
                                            >
                                                <CircleSlash2Icon />
                                            </Button>
                                        )}
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                )}
            </CollapsibleContent>
        </Collapsible>
    );
}
