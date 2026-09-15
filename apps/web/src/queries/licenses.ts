import { queryOptions } from "@tanstack/react-query";
import { api, unwrapEden } from "@/lib/api-client";

const fetchLicenses = async () => {
    const document = await unwrapEden(api.licenses.get());
    if (!document) throw new Error("The license inventory is empty.");
    return document;
};

export type LicenseDocument = Awaited<ReturnType<typeof fetchLicenses>>;

export const licensesQueryOptions = () =>
    queryOptions({
        queryKey: ["licenses"],
        queryFn: fetchLicenses,
        staleTime: Infinity,
        retry: false,
    });
