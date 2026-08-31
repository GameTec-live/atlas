import { mutationOptions, queryOptions } from "@tanstack/react-query";
import { api, unwrapEden } from "@/lib/api-client";

export const setupStatusQueryKey = ["setup", "status"] as const;

export interface SetupAdminInput {
    email: string;
    username: string;
    password: string;
}

export const setupStatusQueryOptions = () =>
    queryOptions({
        queryKey: setupStatusQueryKey,
        queryFn: () => unwrapEden(api.setup.get()),
        staleTime: Number.POSITIVE_INFINITY,
    });

export const setupAdminMutationOptions = () =>
    mutationOptions({
        mutationFn: (input: SetupAdminInput) =>
            unwrapEden(api.setup.admin.post(input)),
    });

export const completeSetupMutationOptions = () =>
    mutationOptions({
        mutationFn: () => unwrapEden(api.setup.complete.post()),
    });
