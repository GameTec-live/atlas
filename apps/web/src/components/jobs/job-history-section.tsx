import { useMemo } from "react";
import { EmptyJobs } from "@/components/jobs/empty-jobs";
import { JobCard } from "@/components/jobs/job-card";
import { JobsSectionHeader } from "@/components/jobs/jobs-section-header";
import { ScrollArea } from "@/components/ui/scroll-area";
import { formatLongDateOrToday, localDateKey } from "@/lib/date";
import { m } from "@/paraglide/messages";
import { getLocale } from "@/paraglide/runtime";
import type { Job } from "@/queries/jobs";
import { Marker, MarkerContent } from "../ui/marker";

export function JobHistorySection({
    jobs,
    onDownload,
}: {
    jobs: Job[];
    onDownload: () => void;
}) {
    const groups = useMemo(() => {
        const groupedJobs = new Map<string, { date: Date; jobs: Job[] }>();
        for (const job of jobs) {
            const key = localDateKey(job.dueDate);
            const group = groupedJobs.get(key);
            if (group) group.jobs.push(job);
            else groupedJobs.set(key, { date: job.dueDate, jobs: [job] });
        }
        return [...groupedJobs.values()];
    }, [jobs]);

    return (
        <section className="flex min-h-128 min-w-0 flex-1 flex-col">
            <JobsSectionHeader
                title={m.jobs_history_title()}
                downloadLabel={m.jobs_download_history()}
                onDownload={onDownload}
            />
            <ScrollArea className="min-h-0 flex-1">
                <div className="mx-auto w-full max-w-5xl p-5 lg:p-6">
                    {groups.length === 0 ? (
                        <EmptyJobs title={m.jobs_empty_history()} />
                    ) : (
                        <div className="space-y-8">
                            {groups.map((group) => (
                                <section key={localDateKey(group.date)}>
                                    <Marker
                                        variant="separator"
                                        className="mb-4 uppercase text-xs font-medium tracking-wide"
                                    >
                                        <MarkerContent>
                                            {formatLongDateOrToday(
                                                group.date,
                                                getLocale(),
                                                m.jobs_today(),
                                            )}
                                        </MarkerContent>
                                    </Marker>
                                    <div className="grid gap-3 xl:grid-cols-2">
                                        {group.jobs.map((job) => (
                                            <JobCard
                                                key={job.id}
                                                job={job}
                                                showHistory
                                            />
                                        ))}
                                    </div>
                                </section>
                            ))}
                        </div>
                    )}
                </div>
            </ScrollArea>
        </section>
    );
}
