import { queryOptions } from "@tanstack/react-query";
import { api, unwrapEden } from "@/lib/api-client";
import { authClient } from "@/lib/auth-client";

export const logbooksQueryKey = ["logbooks"] as const;
export const logbookUsersQueryKey = ["logbooks", "users"] as const;

const fetchLogbooks = async () => {
    const entries = await unwrapEden(api.logbooks.get());
    return entries ?? [];
};

const fetchLogbookUsers = async () => {
    const response = await authClient.admin.listUsers({
        query: {
            sortBy: "name",
            sortDirection: "asc",
        },
    });

    if (response.error) throw response.error;
    return response.data?.users ?? [];
};

export type LogbookEntry = Awaited<ReturnType<typeof fetchLogbooks>>[number];
export type LogbookUser = Awaited<ReturnType<typeof fetchLogbookUsers>>[number];

export const logbooksQueryOptions = () =>
    queryOptions({
        queryKey: logbooksQueryKey,
        queryFn: fetchLogbooks,
    });

export const logbookUsersQueryOptions = () =>
    queryOptions({
        queryKey: logbookUsersQueryKey,
        queryFn: fetchLogbookUsers,
        staleTime: 60_000,
    });
