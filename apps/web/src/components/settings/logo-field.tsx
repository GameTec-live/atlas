import { useMutation } from "@tanstack/react-query";
import { ImageIcon, Trash2Icon, UploadCloudIcon } from "lucide-react";
import { useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Spinner } from "@/components/ui/spinner";
import { toast } from "@/components/ui/toast";
import { apiUrl } from "@/lib/api-url";
import { deleteLogo, uploadLogo } from "@/lib/logo";
import { cn } from "@/lib/utils";
import { m } from "@/paraglide/messages";

const ACCEPTED_LOGO_TYPES =
    "image/avif,image/bmp,image/gif,image/jpeg,image/png,image/svg+xml,image/vnd.microsoft.icon,image/webp,image/x-icon";

export function LogoField() {
    const inputRef = useRef<HTMLInputElement>(null);
    const [isDragging, setIsDragging] = useState(false);
    const [logoCacheKey, setLogoCacheKey] = useState(0);
    const [logoUnavailable, setLogoUnavailable] = useState(false);
    const uploadMutation = useMutation({
        mutationFn: uploadLogo,
        onSuccess: () => {
            setLogoUnavailable(false);
            setLogoCacheKey(Date.now());
            toast.add({
                id: "settings-logo-saved",
                type: "success",
                title: m.settings_logo_saved(),
            });
        },
        onError: () =>
            toast.add({
                id: "settings-logo-error",
                type: "error",
                title: m.settings_logo_error(),
                description: m.settings_logo_requirements(),
                priority: "high",
            }),
    });
    const clearMutation = useMutation({
        mutationFn: deleteLogo,
        onSuccess: () => {
            setLogoUnavailable(true);
            toast.add({
                id: "settings-logo-cleared",
                type: "success",
                title: m.settings_logo_cleared(),
            });
        },
        onError: () =>
            toast.add({
                id: "settings-logo-clear-error",
                type: "error",
                title: m.settings_logo_clear_error(),
                priority: "high",
            }),
    });
    const isPending = uploadMutation.isPending || clearMutation.isPending;
    const upload = (file: File | undefined) =>
        file && uploadMutation.mutate(file);

    return (
        <Field>
            <FieldLabel>{m.settings_logo()}</FieldLabel>
            <div
                className={cn(
                    "group relative flex aspect-square w-full items-center justify-center overflow-hidden rounded-xl border border-dashed bg-muted/30 transition-colors hover:bg-muted/60",
                    isDragging && "border-primary bg-primary/5",
                )}
            >
                <button
                    type="button"
                    className="absolute inset-0 z-10 rounded-xl outline-none focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:ring-inset"
                    aria-label={m.settings_upload_logo()}
                    onClick={() => inputRef.current?.click()}
                    onDragEnter={(event) => {
                        event.preventDefault();
                        setIsDragging(true);
                    }}
                    onDragOver={(event) => event.preventDefault()}
                    onDragLeave={() => setIsDragging(false)}
                    onDrop={(event) => {
                        event.preventDefault();
                        setIsDragging(false);
                        if (!isPending) upload(event.dataTransfer.files[0]);
                    }}
                    disabled={isPending}
                />
                {!logoUnavailable ? (
                    // biome-ignore lint/performance/noImgElement: The uploaded logo is served dynamically by the API.
                    <img
                        src={`${apiUrl}/config/logo?v=${logoCacheKey}`}
                        alt={m.settings_current_logo()}
                        className="h-full w-full object-contain"
                        onError={() => setLogoUnavailable(true)}
                    />
                ) : (
                    <div className="grid place-items-center gap-2 text-muted-foreground">
                        <ImageIcon className="size-10" />
                        <span className="text-xs">{m.settings_no_logo()}</span>
                    </div>
                )}
                <div className="pointer-events-none absolute inset-x-2 bottom-2 z-20 flex h-9 items-center overflow-hidden rounded-lg border bg-background/90 opacity-0 shadow-sm backdrop-blur-sm transition-opacity group-hover:opacity-100 group-focus-within:opacity-100">
                    <span className="flex min-w-0 flex-1 items-center justify-center gap-1.5 px-2 text-xs font-medium">
                        {uploadMutation.isPending ? (
                            <Spinner />
                        ) : (
                            <UploadCloudIcon className="size-4" />
                        )}
                        <span className="truncate">
                            {uploadMutation.isPending
                                ? m.settings_uploading_logo()
                                : logoUnavailable
                                  ? m.settings_logo_upload_action()
                                  : m.settings_logo_replace_action()}
                        </span>
                    </span>
                    {!logoUnavailable && (
                        <>
                            <span className="h-5 w-px bg-border" />
                            <Button
                                type="button"
                                variant="ghost"
                                size="icon-sm"
                                className="pointer-events-none mr-1 text-destructive group-hover:pointer-events-auto hover:bg-destructive/10 hover:text-destructive focus-visible:pointer-events-auto"
                                aria-label={m.settings_clear_logo()}
                                title={m.settings_clear_logo()}
                                onClick={() => clearMutation.mutate()}
                                disabled={isPending}
                            >
                                {clearMutation.isPending ? (
                                    <Spinner />
                                ) : (
                                    <Trash2Icon />
                                )}
                            </Button>
                        </>
                    )}
                </div>
            </div>
            <input
                ref={inputRef}
                type="file"
                accept={ACCEPTED_LOGO_TYPES}
                className="sr-only"
                onChange={(event) => {
                    upload(event.target.files?.[0]);
                    event.target.value = "";
                }}
            />
            <FieldDescription>
                {m.settings_logo_requirements()}
            </FieldDescription>
        </Field>
    );
}
