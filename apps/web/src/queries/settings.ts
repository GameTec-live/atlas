import {
    keepPreviousData,
    mutationOptions,
    queryOptions,
} from "@tanstack/react-query";
import { type AdminUser, listAdminUsers } from "@/lib/admin-users";
import { api, unwrapEden } from "@/lib/api-client";
import type { ValhallaLanguage } from "@/lib/valhalla-languages";

export const settingsUsersQueryKey = ["settings", "users"] as const;
export const settingsConfigQueryKey = ["settings", "config"] as const;
export const apiInfoQueryKey = ["api", "info"] as const;
export const geodataDatasetsQueryKey = ["geodata", "datasets"] as const;
export const geodataCatalogQueryKey = ["geodata", "catalog"] as const;
export const geodataJobsQueryKey = ["geodata", "jobs", "active"] as const;

const fetchConfig = () => unwrapEden(api.config.get());
const fetchApiInfo = () => unwrapEden(api.get());
const fetchGeodataDatasets = () => unwrapEden(api.geodata.datasets.get());
const fetchGeodataCatalog = () =>
    unwrapEden(api.geodata.catalog.get({ query: {} }));
const fetchActiveGeodataJobs = () =>
    unwrapEden(api.geodata.jobs.get({ query: { active: true } }));

export type SettingsUser = AdminUser;
export type SettingsConfig = NonNullable<
    Awaited<ReturnType<typeof fetchConfig>>
>;
type GeodataDatasetsResponse = NonNullable<
    Awaited<ReturnType<typeof fetchGeodataDatasets>>
>;
type GeodataCatalogResponse = NonNullable<
    Awaited<ReturnType<typeof fetchGeodataCatalog>>
>;
type GeodataJobsResponse = NonNullable<
    Awaited<ReturnType<typeof fetchActiveGeodataJobs>>
>;
export type GeodataDataset = GeodataDatasetsResponse["items"][number];
export type GeodataCatalogItem = Exclude<
    GeodataCatalogResponse,
    { error: unknown }
>["items"][number];
export type GeodataJob = GeodataJobsResponse["items"][number];

export interface GeneralSettingsInput {
    defaultLanguage: ValhallaLanguage;
    maxDispatchers: number;
    pricePerKilometer: number;
}

export interface GeodataInstallInput {
    id: string;
    excludeRoads: boolean;
}

export interface GeodataDatasetMutationInput {
    operation: "update" | "delete";
    id: string;
}

export const settingsUsersQueryOptions = () =>
    queryOptions({
        queryKey: settingsUsersQueryKey,
        queryFn: listAdminUsers,
        staleTime: 30_000,
    });

export const settingsConfigQueryOptions = () =>
    queryOptions({
        queryKey: settingsConfigQueryKey,
        queryFn: fetchConfig,
        staleTime: 30_000,
    });

export const apiInfoQueryOptions = () =>
    queryOptions({
        queryKey: apiInfoQueryKey,
        queryFn: fetchApiInfo,
        staleTime: Number.POSITIVE_INFINITY,
    });

export const geodataDatasetsQueryOptions = (isProcessing = false) =>
    queryOptions({
        queryKey: [...geodataDatasetsQueryKey, isProcessing] as const,
        queryFn: fetchGeodataDatasets,
        placeholderData: keepPreviousData,
        refetchInterval: isProcessing ? 2_000 : false,
    });

export const geodataCatalogQueryOptions = () =>
    queryOptions({
        queryKey: geodataCatalogQueryKey,
        queryFn: fetchGeodataCatalog,
        staleTime: 5 * 60_000,
    });

export const geodataJobsQueryOptions = () =>
    queryOptions({
        queryKey: geodataJobsQueryKey,
        queryFn: fetchActiveGeodataJobs,
        staleTime: 5_000,
        refetchInterval: (query) =>
            query.state.data?.items.length ? 2_000 : false,
    });

export const settingsConfigMutationOptions = () =>
    mutationOptions({
        mutationFn: (input: GeneralSettingsInput) =>
            unwrapEden(
                api.config.put({
                    routing: {
                        defaultLanguage: input.defaultLanguage,
                    },
                    dispatchers: { max: input.maxDispatchers },
                    pricing: {
                        pricePerKilometer: input.pricePerKilometer,
                    },
                }),
            ),
    });

export const geodataInstallMutationOptions = () =>
    mutationOptions({
        mutationFn: (input: GeodataInstallInput) =>
            unwrapEden(api.geodata.datasets.post(input)),
    });

export const geodataDatasetMutationOptions = () =>
    mutationOptions({
        mutationFn: ({ operation, id }: GeodataDatasetMutationInput) =>
            operation === "update"
                ? unwrapEden(api.geodata.datasets({ id }).update.post())
                : unwrapEden(api.geodata.datasets({ id }).delete()),
    });

export const geodataJobCancelMutationOptions = () =>
    mutationOptions({
        mutationFn: (id: string) =>
            unwrapEden(api.geodata.jobs({ id }).delete()),
    });
