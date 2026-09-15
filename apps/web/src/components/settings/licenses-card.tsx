import { HeartHandshakeIcon } from "lucide-react";
import { useState } from "react";
import { LicensesDialog } from "@/components/settings/licenses-dialog";
import { Button } from "@/components/ui/button";
import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import { Dialog, DialogTrigger } from "@/components/ui/dialog";
import { m } from "@/paraglide/messages";

export function LicensesCard() {
    const [open, setOpen] = useState(false);
    return (
        <Dialog open={open} onOpenChange={setOpen}>
            <Card className="lg:col-span-12">
                <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                        <HeartHandshakeIcon className="size-4" />
                        {m.settings_licenses_title()}
                    </CardTitle>
                    <CardDescription>
                        {m.settings_licenses_description()}
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    <DialogTrigger render={<Button variant="outline" />}>
                        {m.settings_licenses_open()}
                    </DialogTrigger>
                </CardContent>
            </Card>
            <LicensesDialog open={open} />
        </Dialog>
    );
}
