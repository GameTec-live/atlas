import { queryOptions } from "@tanstack/react-query";
import { api, unwrapEden } from "@/lib/api-client";

export const shortnamesQueryKey = ["shortnames"] as const;

export const shortnamesQueryOptions = () =>
    queryOptions({
        queryKey: shortnamesQueryKey,
        queryFn: () => unwrapEden(api.shortnames.get()),
    });
