import { useForm } from "@tanstack/react-form";
import { DownloadIcon } from "lucide-react";
import * as v from "valibot";
import { Button } from "@/components/ui/button";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import {
    Field,
    FieldDescription,
    FieldError,
    FieldLabel,
} from "@/components/ui/field";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { Spinner } from "@/components/ui/spinner";
import { Switch } from "@/components/ui/switch";
import { formatBytes } from "@/lib/number";
import { m } from "@/paraglide/messages";
import { getLocale } from "@/paraglide/runtime";
import type { GeodataCatalogItem } from "@/queries/settings";

const installSchema = v.object({
    datasetId: v.pipe(
        v.string(),
        v.minLength(1, m.settings_validation_required()),
    ),
    excludeRoads: v.boolean(),
});

export function InstallMapDialog({
    items,
    isPending,
    onClose,
    onInstall,
}: {
    items: GeodataCatalogItem[];
    isPending: boolean;
    onClose: () => void;
    onInstall: (input: { id: string; excludeRoads: boolean }) => void;
}) {
    const form = useForm({
        defaultValues: { datasetId: "", excludeRoads: false },
        validators: { onChange: installSchema, onSubmit: installSchema },
        onSubmit: ({ value }) =>
            onInstall({
                id: value.datasetId,
                excludeRoads: value.excludeRoads,
            }),
    });

    return (
        <Dialog
            open
            onOpenChange={(nextOpen) => {
                if (!nextOpen && !isPending) onClose();
            }}
        >
            <DialogContent className="sm:max-w-md">
                <DialogHeader>
                    <DialogTitle>{m.settings_map_download_title()}</DialogTitle>
                    <DialogDescription>
                        {m.settings_map_download_description()}
                    </DialogDescription>
                </DialogHeader>
                <form
                    className="grid gap-5"
                    onSubmit={(event) => {
                        event.preventDefault();
                        void form.handleSubmit();
                    }}
                >
                    <form.Field name="datasetId">
                        {(field) => (
                            <Field data-invalid={!field.state.meta.isValid}>
                                <FieldLabel htmlFor={field.name}>
                                    {m.settings_map_region()}
                                </FieldLabel>
                                <Select
                                    value={field.state.value}
                                    onValueChange={(value) =>
                                        field.handleChange(value ?? "")
                                    }
                                >
                                    <SelectTrigger
                                        id={field.name}
                                        className="w-full"
                                    >
                                        <SelectValue
                                            placeholder={m.settings_map_choose_region()}
                                        />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {items.map((item) => (
                                            <SelectItem
                                                key={item.id}
                                                value={item.id}
                                            >
                                                <span className="flex w-full items-center justify-between gap-4">
                                                    <span>{item.name}</span>
                                                    {item.size_bytes && (
                                                        <span className="text-xs text-muted-foreground">
                                                            {formatBytes(
                                                                item.size_bytes
                                                                    .total_estimate,
                                                                getLocale(),
                                                            )}
                                                        </span>
                                                    )}
                                                </span>
                                            </SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                                <FieldError errors={field.state.meta.errors} />
                            </Field>
                        )}
                    </form.Field>
                    <form.Field name="excludeRoads">
                        {(field) => (
                            <Field orientation="horizontal">
                                <div className="flex-1">
                                    <FieldLabel htmlFor={field.name}>
                                        {m.settings_map_exclude_roads()}
                                    </FieldLabel>
                                    <FieldDescription>
                                        {m.settings_map_exclude_roads_description()}
                                    </FieldDescription>
                                </div>
                                <Switch
                                    id={field.name}
                                    checked={field.state.value}
                                    onCheckedChange={field.handleChange}
                                />
                            </Field>
                        )}
                    </form.Field>
                    <DialogFooter>
                        <Button
                            type="button"
                            variant="outline"
                            onClick={onClose}
                            disabled={isPending}
                        >
                            {m.settings_cancel()}
                        </Button>
                        <form.Subscribe selector={(state) => state.canSubmit}>
                            {(canSubmit) => (
                                <Button
                                    type="submit"
                                    disabled={!canSubmit || isPending}
                                >
                                    {isPending ? <Spinner /> : <DownloadIcon />}
                                    {m.settings_map_download()}
                                </Button>
                            )}
                        </form.Subscribe>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    );
}
