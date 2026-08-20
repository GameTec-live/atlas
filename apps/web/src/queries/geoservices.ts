import { queryOptions } from "@tanstack/react-query";
import { api, unwrapEden } from "@/lib/api-client";

export const addressResolveQueryKey = ["geoservices", "resolve"] as const;
export const routeQueryKey = ["geoservices", "route"] as const;

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

export const routeQueryOptions = (
    from: [number, number],
    to: [number, number] | null,
) =>
    queryOptions({
        queryKey: [...routeQueryKey, from, to] as const,
        queryFn: () => {
            if (!to) throw new Error("A destination is required for routing");

            return unwrapEden(
                api.geoservices.route.get({
                    query: {
                        fromlat: from[0],
                        fromlon: from[1],
                        tolat: to[0],
                        tolon: to[1],
                    },
                }),
            );
        },
        enabled: to !== null,
        staleTime: 10 * 60_000,
    });
