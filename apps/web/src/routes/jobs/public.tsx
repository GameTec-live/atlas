import { useSuspenseQuery } from "@tanstack/react-query";
import {
    createFileRoute,
    type ErrorComponentProps,
} from "@tanstack/react-router";
import { AlertCircleIcon, KeyRoundIcon } from "lucide-react";
import { type ReactNode, useMemo } from "react";
import { EmptyJobs } from "@/components/jobs/empty-jobs";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { formatDateTime } from "@/lib/date";
import { formatJobLocation, getJobAddresses } from "@/lib/jobs";
import { m } from "@/paraglide/messages";
import { getLocale } from "@/paraglide/runtime";
import { type PublicJob, publicJobsQueryOptions } from "@/queries/jobs";

export const Route = createFileRoute("/jobs/public")({
    validateSearch: ({ jobtoken }) => ({
        jobtoken:
            typeof jobtoken === "string" && jobtoken.length > 0
                ? jobtoken
                : undefined,
    }),
    loaderDeps: ({ search: { jobtoken } }) => ({ jobtoken }),
    loader: ({ context, deps: { jobtoken } }) =>
        jobtoken
            ? context.queryClient.ensureQueryData(
                  publicJobsQueryOptions(jobtoken),
              )
            : undefined,
    pendingComponent: PublicJobsSkeleton,
    errorComponent: PublicJobsError,
    component: PublicJobsPage,
});

function PublicJobCard({ job }: { job: PublicJob }) {
    const addresses = getJobAddresses(job);
    const details = [
        {
            label: m.jobs_from(),
            value: formatJobLocation(
                addresses.from,
                job.from,
                m.jobs_not_available(),
            ),
        },
        {
            label: m.jobs_to(),
            value: formatJobLocation(
                addresses.to,
                job.to,
                m.jobs_not_available(),
            ),
        },
        {
            label: m.jobs_due(),
            value: formatDateTime(
                job.dueDate,
                getLocale(),
                m.jobs_not_available(),
            ),
        },
        {
            label: m.jobs_note(),
            value: job.note ?? m.jobs_not_available(),
        },
    ];

    return (
        <Card size="sm">
            <CardContent>
                <dl className="grid gap-2 text-sm">
                    {details.map(({ label, value }) => (
                        <div
                            key={label}
                            className="grid grid-cols-[4rem_1fr] gap-3"
                        >
                            <dt className="text-muted-foreground">{label}</dt>
                            <dd className="min-w-0 wrap-break-word font-medium">
                                {value}
                            </dd>
                        </div>
                    ))}
                </dl>
            </CardContent>
        </Card>
    );
}

function PublicJobsPage() {
    const { jobtoken } = Route.useSearch();

    if (!jobtoken) {
        return (
            <PublicJobsMessage
                icon={<KeyRoundIcon />}
                title={m.jobs_public_missing_token_title()}
                description={m.jobs_public_missing_token_description()}
            />
        );
    }

    return <PublicJobsList jobtoken={jobtoken} />;
}

function PublicJobsList({ jobtoken }: { jobtoken: string }) {
    const { data: jobs } = useSuspenseQuery(publicJobsQueryOptions(jobtoken));
    const newestFirst = useMemo(
        () =>
            [...jobs].sort(
                (left, right) =>
                    right.dueDate.getTime() - left.dueDate.getTime(),
            ),
        [jobs],
    );

    return (
        <PublicJobsLayout>
            {newestFirst.length === 0 ? (
                <EmptyJobs title={m.jobs_empty_unassigned()} />
            ) : (
                <div className="grid gap-3">
                    {newestFirst.map((job) => (
                        <PublicJobCard key={job.id} job={job} />
                    ))}
                </div>
            )}
        </PublicJobsLayout>
    );
}

function PublicJobsLayout({ children }: { children: ReactNode }) {
    return (
        <main className="min-h-svh bg-muted/20 px-4 py-10 sm:px-6">
            <section className="mx-auto w-full max-w-xl">
                <h1 className="mb-5 font-heading text-2xl font-semibold tracking-tight">
                    {m.jobs_unassigned_title()}
                </h1>
                {children}
            </section>
        </main>
    );
}

function PublicJobsSkeleton() {
    return (
        <PublicJobsLayout>
            <div className="grid gap-3">
                {["one", "two", "three", "four"].map((key) => (
                    <Skeleton key={key} className="h-36" />
                ))}
            </div>
        </PublicJobsLayout>
    );
}

function PublicJobsMessage({
    icon,
    title,
    description,
    action,
}: {
    icon: ReactNode;
    title: string;
    description: string;
    action?: ReactNode;
}) {
    return (
        <PublicJobsLayout>
            <Alert variant="destructive">
                {icon}
                <AlertTitle>{title}</AlertTitle>
                <AlertDescription>{description}</AlertDescription>
            </Alert>
            {action && <div className="mt-4">{action}</div>}
        </PublicJobsLayout>
    );
}

function PublicJobsError({ reset }: ErrorComponentProps) {
    return (
        <PublicJobsMessage
            icon={<AlertCircleIcon />}
            title={m.jobs_public_load_error_title()}
            description={m.jobs_public_load_error_description()}
            action={
                <Button variant="outline" onClick={reset}>
                    {m.mushy_salty_kitten_stab()}
                </Button>
            }
        />
    );
}
