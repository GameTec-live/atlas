import { useForm } from "@tanstack/react-form";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ChevronDownIcon, FileUpIcon, RotateCcwIcon } from "lucide-react";
import * as v from "valibot";
import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle,
    AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import {
    Collapsible,
    CollapsibleContent,
    CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import { Spinner } from "@/components/ui/spinner";
import { toast } from "@/components/ui/toast";
import { m } from "@/paraglide/messages";
import {
    type SystemUpdate,
    systemRollbackMutationOptions,
    systemUpdateQueryKey,
    systemUploadUpdateMutationOptions,
    systemUrlUpdateMutationOptions,
} from "@/queries/system";

const urlSchema = v.object({
    url: v.pipe(v.string(), v.url("Enter a valid HTTP or HTTPS URL.")),
});
const fileSchema = v.object({
    file: v.custom<File>(
        (file) => file instanceof File && file.size > 0,
        "Choose an Atlas OS update bundle.",
    ),
});

export function SystemUpdateAdvanced({
    update,
    onInstalled,
    onError,
    onRolledBack,
}: {
    update: SystemUpdate;
    onInstalled: () => void;
    onError: (error: unknown) => void;
    onRolledBack: () => void;
}) {
    const queryClient = useQueryClient();
    const urlMutation = useMutation({
        ...systemUrlUpdateMutationOptions(),
        onSuccess: onInstalled,
        onError,
    });
    const uploadMutation = useMutation({
        ...systemUploadUpdateMutationOptions(),
        onSuccess: onInstalled,
        onError,
    });
    const rollbackMutation = useMutation({
        ...systemRollbackMutationOptions(),
        onSuccess: async () => {
            await queryClient.invalidateQueries({
                queryKey: systemUpdateQueryKey,
            });
            toast.add({
                id: "system-rollback-started",
                type: "success",
                title: m.settings_system_rollback_started(),
            });
            onRolledBack();
        },
        onError,
    });
    const urlForm = useForm({
        defaultValues: { url: "" },
        validators: { onChange: urlSchema, onSubmit: urlSchema },
        onSubmit: ({ value }) => urlMutation.mutate(value.url),
    });
    const fileForm = useForm({
        defaultValues: { file: undefined as File | undefined },
        validators: { onSubmit: fileSchema },
        onSubmit: ({ value }) => {
            if (value.file) uploadMutation.mutate(value.file);
        },
    });

    return (
        <Collapsible>
            <CollapsibleTrigger
                render={
                    <Button
                        variant="ghost"
                        className="group w-full justify-between"
                    />
                }
            >
                {m.settings_system_advanced()}
                <ChevronDownIcon className="transition-transform group-data-panel-open:rotate-180" />
            </CollapsibleTrigger>
            <CollapsibleContent className="grid gap-4 pt-3">
                <form
                    className="grid gap-3"
                    onSubmit={(event) => {
                        event.preventDefault();
                        void urlForm.handleSubmit();
                    }}
                >
                    <urlForm.Field name="url">
                        {(field) => (
                            <Field data-invalid={!field.state.meta.isValid}>
                                <FieldLabel htmlFor={field.name}>
                                    {m.settings_system_update_url()}
                                </FieldLabel>
                                <div className="flex gap-2">
                                    <Input
                                        id={field.name}
                                        type="url"
                                        placeholder="https://…/atlas-update.tar.zst"
                                        value={field.state.value}
                                        onBlur={field.handleBlur}
                                        onChange={(event) =>
                                            field.handleChange(
                                                event.target.value,
                                            )
                                        }
                                        aria-invalid={!field.state.meta.isValid}
                                    />
                                    <urlForm.Subscribe
                                        selector={(state) => state.values.url}
                                    >
                                        {(url) => (
                                            <Button
                                                type="submit"
                                                variant="secondary"
                                                disabled={
                                                    !url ||
                                                    !field.state.meta.isValid ||
                                                    urlMutation.isPending
                                                }
                                            >
                                                {urlMutation.isPending && (
                                                    <Spinner />
                                                )}
                                                {m.settings_system_install()}
                                            </Button>
                                        )}
                                    </urlForm.Subscribe>
                                </div>
                                <FieldError errors={field.state.meta.errors} />
                            </Field>
                        )}
                    </urlForm.Field>
                </form>

                <Separator />

                <form
                    className="grid gap-3"
                    onSubmit={(event) => {
                        event.preventDefault();
                        void fileForm.handleSubmit();
                    }}
                >
                    <fileForm.Field name="file">
                        {(field) => (
                            <Field data-invalid={!field.state.meta.isValid}>
                                <FieldLabel htmlFor={field.name}>
                                    {m.settings_system_update_file()}
                                </FieldLabel>
                                <Input
                                    id={field.name}
                                    type="file"
                                    accept=".zst,.tar.zst,application/zstd,application/octet-stream"
                                    onBlur={field.handleBlur}
                                    onChange={(event) =>
                                        field.handleChange(
                                            event.target.files?.[0],
                                        )
                                    }
                                    aria-invalid={!field.state.meta.isValid}
                                />
                                <FieldError errors={field.state.meta.errors} />
                            </Field>
                        )}
                    </fileForm.Field>
                    <fileForm.Subscribe selector={(state) => state.values.file}>
                        {(file) => (
                            <Button
                                type="submit"
                                variant="secondary"
                                disabled={!file || uploadMutation.isPending}
                            >
                                {uploadMutation.isPending ? (
                                    <Spinner />
                                ) : (
                                    <FileUpIcon />
                                )}
                                {m.settings_system_upload_install()}
                            </Button>
                        )}
                    </fileForm.Subscribe>
                </form>

                <Separator />

                <AlertDialog>
                    <AlertDialogTrigger
                        render={
                            <Button
                                type="button"
                                variant="outline"
                                className="w-full"
                            />
                        }
                    >
                        <RotateCcwIcon />
                        {m.settings_system_rollback()}
                    </AlertDialogTrigger>
                    <AlertDialogContent>
                        <AlertDialogHeader>
                            <AlertDialogTitle>
                                {m.settings_system_rollback_title()}
                            </AlertDialogTitle>
                            <AlertDialogDescription>
                                {m.settings_system_rollback_description({
                                    slot: update.update.other,
                                })}
                            </AlertDialogDescription>
                        </AlertDialogHeader>
                        <AlertDialogFooter>
                            <AlertDialogCancel>
                                {m.settings_cancel()}
                            </AlertDialogCancel>
                            <AlertDialogAction
                                variant="destructive"
                                disabled={rollbackMutation.isPending}
                                onClick={() => rollbackMutation.mutate()}
                            >
                                {rollbackMutation.isPending && <Spinner />}
                                {m.settings_system_rollback()}
                            </AlertDialogAction>
                        </AlertDialogFooter>
                    </AlertDialogContent>
                </AlertDialog>
            </CollapsibleContent>
        </Collapsible>
    );
}
