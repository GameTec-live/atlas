import { DownloadIcon } from "lucide-react";
import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";

export function JobsSectionHeader({
    title,
    downloadLabel,
    onDownload,
    action,
}: {
    title: string;
    downloadLabel: string;
    onDownload: () => void;
    action?: ReactNode;
}) {
    return (
        <div className="flex h-16 shrink-0 items-center justify-between gap-3 border-b px-5 lg:px-6">
            <h1 className="font-heading text-xl font-semibold tracking-tight">
                {title}
            </h1>
            <div className="flex items-center">
                <Button
                    variant="ghost"
                    size="icon"
                    aria-label={downloadLabel}
                    title={downloadLabel}
                    onClick={onDownload}
                >
                    <DownloadIcon />
                </Button>
                {action}
            </div>
        </div>
    );
}
