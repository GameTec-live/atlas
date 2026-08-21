import { queryOptions } from "@tanstack/react-query";
import { api, unwrapEden } from "@/lib/api-client";
import { getJobAddresses } from "@/lib/jobs";

export const jobsQueryKey = ["jobs", "all"] as const;
export const jobQueryKey = (id: string) => ["jobs", "detail", id] as const;
export const jobCandidatesQueryKey = ["jobs", "candidates"] as const;

const fetchJobs = async () => {
    const jobs = await unwrapEden(
        api.jobs.all.get({
            query: { filter: "all", geocode: "true" },
        }),
    );
    return jobs ?? [];
};

export type Job = Awaited<ReturnType<typeof fetchJobs>>[number];

const fetchJob = async (id: string) => {
    const job = await unwrapEden(
        api.jobs({ id }).get({
            query: { geocode: "true" },
        }),
    );

    if (!job) throw new Error("Job not found");

    const { from: fromAddress, to: toAddress } = getJobAddresses(job);

    return { ...job, fromAddress, toAddress };
};

export type JobDetail = Awaited<ReturnType<typeof fetchJob>>;

export const jobQueryOptions = (id: string) =>
    queryOptions({
        queryKey: jobQueryKey(id),
        queryFn: () => fetchJob(id),
    });

type CandidateTarget = {
    from: [number, number];
    to: [number, number] | null;
    dueDate: Date;
};

const fetchJobCandidates = async (target: CandidateTarget) => {
    const candidates = await unwrapEden(
        api.jobs.candidates.post({
            from: target.from,
            dueDate: target.dueDate,
            ...(target.to ? { to: target.to } : {}),
        }),
    );
    return candidates ?? [];
};

export type JobCandidate = Awaited<
    ReturnType<typeof fetchJobCandidates>
>[number];

export const jobCandidatesQueryOptions = (target: CandidateTarget) =>
    queryOptions({
        queryKey: [
            ...jobCandidatesQueryKey,
            target.from,
            target.to,
            target.dueDate.toISOString(),
        ],
        queryFn: () => fetchJobCandidates(target),
    });

export const jobsQueryOptions = () =>
    queryOptions({
        queryKey: jobsQueryKey,
        queryFn: fetchJobs,
    });
