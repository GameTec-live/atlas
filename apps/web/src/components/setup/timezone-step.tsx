import { useQuery } from "@tanstack/react-query";
import { Clock3Icon } from "lucide-react";
import { SystemTimezoneForm } from "@/components/settings/system/system-timezone-form";
import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { m } from "@/paraglide/messages";
import { systemTimezoneQueryOptions } from "@/queries/system";

export function TimezoneStep() {
    const timezone = useQuery(systemTimezoneQueryOptions());

    return (
        <Card className="mx-auto max-w-lg">
            <CardHeader>
                <CardTitle className="flex items-center gap-2">
                    <Clock3Icon className="size-4" />
                    {m.settings_system_timezone()}
                </CardTitle>
                <CardDescription>
                    {m.settings_system_timezone_description()}
                </CardDescription>
            </CardHeader>
            <CardContent>
                {timezone.isPending ? (
                    <Skeleton className="h-9 w-full max-w-lg" />
                ) : timezone.data ? (
                    <SystemTimezoneForm
                        key={timezone.data.timezone}
                        timezone={timezone.data.timezone}
                    />
                ) : (
                    <p className="text-sm text-destructive">
                        {m.settings_system_timezone_error()}
                    </p>
                )}
            </CardContent>
        </Card>
    );
}
