import { CloudIcon } from "lucide-react";
import { SystemRemoteAccess } from "@/components/settings/system/system-remote-access";
import { m } from "@/paraglide/messages";
import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "../ui/card";

export function RemoteAccessStep() {
    return (
        <Card className="mx-auto">
            <CardHeader>
                <CardTitle className="flex items-center gap-2">
                    <CloudIcon className="size-4" />
                    {m.settings_system_remote_access()}
                </CardTitle>
                <CardDescription>
                    {m.settings_system_remote_access_description()}
                </CardDescription>
            </CardHeader>
            <CardContent>
                <SystemRemoteAccess header={false} />
            </CardContent>
        </Card>
    );
}
