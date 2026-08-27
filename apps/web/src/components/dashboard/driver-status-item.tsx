import { Link } from "@tanstack/react-router";
import { ArrowRightIcon } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import type { LiveDriver } from "@/hooks/use-live-drivers";
import { driverStateBackgrounds, driverStateLabels } from "@/lib/drivers";
import { formatJobLocation, getJobAddresses } from "@/lib/jobs";
import { m } from "@/paraglide/messages";
import type { Job } from "@/queries/jobs";

export function DriverStatusItem({
    driver,
    job,
}: {
    driver: LiveDriver;
    job?: Job;
}) {
    const addresses = job ? getJobAddresses(job) : null;

    return (
        <div className="rounded-lg border p-3">
            <div className="flex items-center gap-2">
                <span className="min-w-0 flex-1 truncate font-medium">
                    {driver.userName}
                </span>
                <Badge variant="outline">
                    <span
                        className={`size-1.5 rounded-full ${driverStateBackgrounds[driver.state]}`}
                    />
                    {driverStateLabels[driver.state]}
                </Badge>
            </div>
            {job && addresses ? (
                <Link
                    to="/jobs/$jobId"
                    params={{ jobId: job.id }}
                    className="mt-2 grid grid-cols-[auto_minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-1.5 rounded-md bg-muted/60 p-2 text-xs transition-colors hover:bg-muted"
                >
                    <span className="text-muted-foreground">
                        {m.jobs_from()}
                    </span>
                    <span className="truncate">
                        {formatJobLocation(
                            addresses.from,
                            job.from,
                            m.jobs_not_available(),
                        )}
                    </span>
                    <ArrowRightIcon className="size-3 text-muted-foreground" />
                    <span className="truncate">
                        {formatJobLocation(
                            addresses.to,
                            job.to,
                            m.jobs_not_available(),
                        )}
                    </span>
                </Link>
            ) : (
                <p className="mt-2 px-2 text-xs text-muted-foreground">
                    {m.dashboard_drivers_no_job()}
                </p>
            )}
        </div>
    );
}
