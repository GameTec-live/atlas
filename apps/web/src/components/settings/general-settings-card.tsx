import { useQuery } from "@tanstack/react-query";
import { Settings2Icon } from "lucide-react";
import { GeneralSettingsForm } from "@/components/settings/general-settings-form";
import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { m } from "@/paraglide/messages";
import { settingsConfigQueryOptions } from "@/queries/settings";

export function GeneralSettingsCard() {
    const configQuery = useQuery(settingsConfigQueryOptions());

    return (
        <Card className="lg:col-span-12">
            <CardHeader>
                <CardTitle className="flex items-center gap-2">
                    <Settings2Icon className="size-4" />
                    {m.settings_general()}
                </CardTitle>
                <CardDescription>
                    {m.settings_general_description()}
                </CardDescription>
            </CardHeader>
            <CardContent>
                {configQuery.isPending ? (
                    <div className="grid gap-6 md:grid-cols-[11rem_1fr]">
                        <Skeleton className="aspect-square rounded-xl" />
                        <div className="grid content-start gap-4 sm:grid-cols-2">
                            <Skeleton className="h-16" />
                            <Skeleton className="h-16" />
                            <Skeleton className="h-16" />
                        </div>
                    </div>
                ) : configQuery.isError || !configQuery.data ? (
                    <p className="text-sm text-destructive">
                        {m.settings_general_error()}
                    </p>
                ) : (
                    <GeneralSettingsForm
                        key={`${configQuery.data.dispatchers.max}:${configQuery.data.pricing.pricePerKilometer}:${configQuery.data.routing.defaultLanguage}`}
                        config={configQuery.data}
                    />
                )}
            </CardContent>
        </Card>
    );
}
