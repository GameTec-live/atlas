import { createFileRoute } from "@tanstack/react-router";
import { GeneralSettingsCard } from "@/components/settings/general-settings-card";
import { MapDataCard } from "@/components/settings/map-data-card";
import { SettingsPageSkeleton } from "@/components/settings/settings-page-skeleton";
import { SystemCard } from "@/components/settings/system/system-card";
import { UsersCard } from "@/components/settings/users-card";
import { m } from "@/paraglide/messages";
import {
    geodataCatalogQueryOptions,
    geodataDatasetsQueryOptions,
    geodataJobsQueryOptions,
    settingsConfigQueryOptions,
    settingsUsersQueryOptions,
} from "@/queries/settings";
import {
    apiInfoQueryOptions,
    systemAvailabilityQueryOptions,
} from "@/queries/system";

export const Route = createFileRoute("/_authenticated/_app/settings/")({
    loader: async ({ context }) => {
        const critical = await Promise.all([
            context.queryClient.prefetchQuery(settingsUsersQueryOptions()),
            context.queryClient.prefetchQuery(settingsConfigQueryOptions()),
        ]);
        const deferred = Promise.all([
            context.queryClient.prefetchQuery(apiInfoQueryOptions()),
            context.queryClient.prefetchQuery(systemAvailabilityQueryOptions()),
            context.queryClient.prefetchQuery(geodataDatasetsQueryOptions()),
            context.queryClient.prefetchQuery(geodataCatalogQueryOptions()),
            context.queryClient.prefetchQuery(geodataJobsQueryOptions()),
        ]);
        return { critical, deferred };
    },
    pendingComponent: SettingsPageSkeleton,
    component: Settings,
});

function Settings() {
    return (
        <main className="mx-auto w-full max-w-7xl p-4 sm:p-6 lg:p-8">
            <h1 className="font-heading text-xl font-semibold tracking-tight mb-6">
                {m.factual_happy_falcon_arise()}
            </h1>

            <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
                <UsersCard />
                <GeneralSettingsCard />
                <SystemCard />
                <MapDataCard />
            </div>
        </main>
    );
}
