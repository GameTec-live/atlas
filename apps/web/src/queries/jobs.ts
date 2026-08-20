import { queryOptions } from "@tanstack/react-query";
import { api, unwrapEden } from "@/lib/api-client";

export const jobsQueryKey = ["jobs", "all"] as const;

const fetchJobs = async () => {
    const jobs = await unwrapEden(
        api.jobs.all.get({
            query: { filter: "all", geocode: "true" },
        }),
    );
    return jobs ?? [];
};

export type Job = Awaited<ReturnType<typeof fetchJobs>>[number];

export const jobsQueryOptions = () =>
    queryOptions({
        queryKey: jobsQueryKey,
        queryFn: fetchJobs,
    });
