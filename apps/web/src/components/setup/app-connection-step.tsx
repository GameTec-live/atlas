import {
    DownloadIcon,
    ExternalLinkIcon,
    LinkIcon,
    SmartphoneIcon,
} from "lucide-react";
import { QRCodeSVG } from "qrcode.react";
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
    Card,
    CardContent,
    CardDescription,
    CardFooter,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import { m } from "@/paraglide/messages";

const PLAY_STORE_URL =
    "https://play.google.com/store/apps/details?id=org.gtlv.atlas";

function QrCode({
    value,
    title,
    description,
    label,
}: {
    value: string;
    title: string;
    description: string;
    label: string;
}) {
    const [open, setOpen] = useState(false);
    const triggerLabel = m.setup_app_enlarge_qr({ name: label });

    return (
        <Dialog open={open} onOpenChange={setOpen}>
            <Button
                type="button"
                variant="ghost"
                aria-label={triggerLabel}
                onClick={() => setOpen(true)}
                className="block size-52 cursor-zoom-in rounded-xl bg-white p-4 shadow-sm transition-transform hover:scale-[1.02] hover:bg-white"
            >
                <QRCodeSVG
                    value={value}
                    level="M"
                    marginSize={0}
                    className="size-full"
                    aria-hidden="true"
                />
            </Button>
            <DialogContent className="justify-items-center sm:max-w-md">
                <DialogHeader className="w-full pr-8 text-left">
                    <DialogTitle>{title}</DialogTitle>
                    <DialogDescription>{description}</DialogDescription>
                </DialogHeader>
                <div className="aspect-square w-full max-w-80 rounded-xl bg-white p-4">
                    <QRCodeSVG
                        value={value}
                        title={label}
                        level="M"
                        marginSize={0}
                        className="size-full"
                    />
                </div>
            </DialogContent>
        </Dialog>
    );
}

export function AppConnectionStep() {
    const deploymentUrl = window.location.origin;
    const pairingUrl = `atlas://${encodeURIComponent(deploymentUrl)}`;

    return (
        <div className="mx-auto grid max-w-4xl gap-5 md:grid-cols-2">
            <Card>
                <CardHeader>
                    <CardTitle>{m.setup_app_download_title()}</CardTitle>
                    <CardDescription className="min-h-10">
                        {m.setup_app_download_description()}
                    </CardDescription>
                </CardHeader>
                <CardContent className="flex justify-center py-2">
                    <QrCode
                        value={PLAY_STORE_URL}
                        title={m.setup_app_download_title()}
                        description={m.setup_app_download_description()}
                        label={m.setup_app_download_qr_label()}
                    />
                </CardContent>
                <CardFooter>
                    <Button
                        variant="outline"
                        className="w-full"
                        nativeButton={false}
                        render={
                            <a
                                href={PLAY_STORE_URL}
                                target="_blank"
                                rel="noreferrer"
                            />
                        }
                    >
                        {m.setup_app_open_store()}
                        <ExternalLinkIcon data-icon="inline-end" />
                    </Button>
                </CardFooter>
            </Card>

            <Card>
                <CardHeader>
                    <CardTitle>{m.setup_app_pair_title()}</CardTitle>
                    <CardDescription className="min-h-10">
                        {m.setup_app_pair_description()}
                    </CardDescription>
                </CardHeader>
                <CardContent className="flex justify-center py-2">
                    <QrCode
                        value={pairingUrl}
                        title={m.setup_app_pair_title()}
                        description={m.setup_app_pair_description()}
                        label={m.setup_app_pair_qr_label({
                            url: deploymentUrl,
                        })}
                    />
                </CardContent>
                <CardFooter>
                    <Button
                        variant="outline"
                        className="w-full"
                        nativeButton={false}
                        render={<a href={pairingUrl} />}
                    >
                        {m.setup_app_pair_action()}
                        <SmartphoneIcon data-icon="inline-end" />
                    </Button>
                </CardFooter>
            </Card>
        </div>
    );
}
