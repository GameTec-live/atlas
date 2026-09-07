import { queryOptions } from "@tanstack/react-query";
import { api, unwrapEden } from "@/lib/api-client";

export const driversQueryKey = ["drivers"] as const;

const fetchDrivers = async () => {
    return (await unwrapEden(api.drivers.get())) ?? [];
};

export type Driver = Awaited<ReturnType<typeof fetchDrivers>>[number];

export const driversQueryOptions = () =>
    queryOptions({
        queryKey: driversQueryKey,
        queryFn: fetchDrivers,
        staleTime: 60_000,
    });
