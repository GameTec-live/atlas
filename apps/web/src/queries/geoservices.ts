import { queryOptions } from "@tanstack/react-query";
import { api, unwrapEden } from "@/lib/api-client";

export const addressResolveQueryKey = ["geoservices", "resolve"] as const;

export const addressResolveQueryOptions = (address: string) =>
    queryOptions({
        queryKey: [...addressResolveQueryKey, address] as const,
        queryFn: () =>
            unwrapEden(
                api.geoservices.resolve.get({
                    query: { address },
                }),
            ),
        enabled: address.length >= 3,
        staleTime: 5 * 60_000,
    });
