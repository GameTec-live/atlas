import { type LucideIcon, Trash2Icon } from "lucide-react";
import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import { Spinner } from "@/components/ui/spinner";
import { formatSystemState } from "@/lib/system";
import { m } from "@/paraglide/messages";

interface ProviderStatus {
    provisioned: boolean;
    state: string;
    detail?: string;
}

export function SystemRemoteProvider({
    icon: Icon,
    name,
    status,
    pending,
    onRemove,
    children,
}: {
    icon: LucideIcon;
    name: string;
    status: ProviderStatus;
    pending: boolean;
    onRemove: () => void;
    children: ReactNode;
}) {
    return (
        <Card>
            <CardHeader>
                <CardTitle className="flex items-center gap-2">
                    <Icon className="size-4" /> {name}
                </CardTitle>
                <CardDescription>
                    {status.detail ?? formatSystemState(status.state)}
                </CardDescription>
            </CardHeader>
            <CardContent className="h-full">
                {status.provisioned ? (
                    <div className="flex justify-center items-center h-full">
                        <Button
                            type="button"
                            variant="outline"
                            disabled={pending}
                            onClick={onRemove}
                        >
                            {pending && <Spinner />}
                            <Trash2Icon />
                            {m.settings_system_remove()}
                        </Button>
                    </div>
                ) : (
                    children
                )}
            </CardContent>
        </Card>
    );
}
