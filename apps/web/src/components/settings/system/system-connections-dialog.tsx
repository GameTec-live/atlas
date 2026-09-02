import { CableIcon, CloudIcon, NetworkIcon } from "lucide-react";
import { SystemNetworkSettings } from "@/components/settings/system/system-network-settings";
import { SystemRemoteAccess } from "@/components/settings/system/system-remote-access";
import { Button } from "@/components/ui/button";
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogTrigger,
} from "@/components/ui/dialog";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { m } from "@/paraglide/messages";

export function SystemConnectionsDialog() {
    return (
        <Dialog>
            <DialogTrigger render={<Button variant="outline" size="sm" />}>
                {m.settings_system_manage_connections()}
            </DialogTrigger>
            <DialogContent className="max-h-11/12 overflow-y-auto sm:max-w-3xl">
                <DialogHeader>
                    <DialogTitle className="flex items-center gap-2">
                        <NetworkIcon className="size-4" />
                        {m.settings_system_connection_settings()}
                    </DialogTitle>
                </DialogHeader>
                <Tabs defaultValue="network">
                    <TabsList className="w-full">
                        <TabsTrigger value="network">
                            <CableIcon className="size-4" />
                            {m.settings_system_connection()}
                        </TabsTrigger>
                        <TabsTrigger value="remote">
                            <CloudIcon className="size-4" />
                            {m.settings_system_remote_access()}
                        </TabsTrigger>
                    </TabsList>
                    <TabsContent value="network">
                        <SystemNetworkSettings />
                    </TabsContent>
                    <TabsContent value="remote">
                        <SystemRemoteAccess />
                    </TabsContent>
                </Tabs>
            </DialogContent>
        </Dialog>
    );
}
