import { Link } from "@tanstack/react-router";
import { Trash2Icon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { formatDateTime } from "@/lib/date";
import { formatJobLocation, getJobAddresses } from "@/lib/jobs";
import { m } from "@/paraglide/messages";
import { getLocale } from "@/paraglide/runtime";
import type { Job } from "@/queries/jobs";

function JobDetails({ job, showHistory }: { job: Job; showHistory: boolean }) {
    const addresses = getJobAddresses(job);
    const locale = getLocale();
    const details = [
        [
            m.jobs_from(),
            formatJobLocation(addresses.from, job.from, m.jobs_not_available()),
        ],
        [
            m.jobs_to(),
            formatJobLocation(addresses.to, job.to, m.jobs_not_available()),
        ],
        [
            m.jobs_due(),
            formatDateTime(job.dueDate, locale, m.jobs_not_available()),
        ],
        ...(showHistory
            ? [
                  [
                      m.jobs_started(),
                      formatDateTime(
                          job.startedAt,
                          locale,
                          m.jobs_not_available(),
                      ),
                  ],
                  [
                      m.jobs_completed(),
                      formatDateTime(
                          job.completedAt,
                          locale,
                          m.jobs_not_available(),
                      ),
                  ],
                  [
                      m.jobs_assigned(),
                      job.assignedDriverName ??
                          job.assignedDriverId ??
                          m.jobs_unassigned(),
                  ],
              ]
            : []),
    ];

    return (
        <dl className="grid gap-1.5 text-sm">
            {details.map(([label, value]) => (
                <div key={label} className="grid grid-cols-[6rem_1fr] gap-2">
                    <dt className="text-muted-foreground">{label}</dt>
                    <dd className="min-w-0 truncate font-medium" title={value}>
                        {value}
                    </dd>
                </div>
            ))}
        </dl>
    );
}

export function JobCard({
    job,
    showHistory = false,
    onDelete,
}: {
    job: Job;
    showHistory?: boolean;
    onDelete?: (job: Job) => void;
}) {
    const addresses = getJobAddresses(job);
    const from = formatJobLocation(
        addresses.from,
        job.from,
        m.jobs_not_available(),
    );

    return (
        <Card className="group/job py-0 transition-colors hover:border-foreground hover:bg-muted">
            <div className="flex items-stretch">
                <Link
                    to="/jobs/$jobId"
                    params={{ jobId: job.id }}
                    className="min-w-0 flex-1 p-4 focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring"
                    aria-label={`${m.jobs_from()} ${from}`}
                >
                    <JobDetails job={job} showHistory={showHistory} />
                    {job.note && (
                        <p className="mt-3 line-clamp-2 border-t pt-3 text-sm">
                            {job.note}
                        </p>
                    )}
                </Link>
                {onDelete && (
                    <div className="flex items-start p-2">
                        <Button
                            variant="ghost"
                            size="icon-sm"
                            className="text-muted-foreground hover:text-destructive"
                            aria-label={m.jobs_delete_label()}
                            title={m.jobs_delete_label()}
                            onClick={() => onDelete(job)}
                        >
                            <Trash2Icon />
                        </Button>
                    </div>
                )}
            </div>
        </Card>
    );
}
