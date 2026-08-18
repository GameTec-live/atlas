import { DownloadIcon } from "lucide-react";
import { Button } from "@/components/ui/button";

export function JobsSectionHeader({
    title,
    downloadLabel,
    onDownload,
}: {
    title: string;
    downloadLabel: string;
    onDownload: () => void;
}) {
    return (
        <div className="flex h-16 shrink-0 items-center justify-between gap-3 border-b px-5 lg:px-6">
            <h1 className="font-heading text-xl font-semibold tracking-tight">
                {title}
            </h1>
            <Button
                variant="ghost"
                size="icon"
                aria-label={downloadLabel}
                title={downloadLabel}
                onClick={onDownload}
            >
                <DownloadIcon />
            </Button>
        </div>
    );
}
