import { useForm } from "@tanstack/react-form";
import { useMutation, useQueryClient } from "@tanstack/react-query";
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
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { Switch } from "@/components/ui/switch";
import { toast } from "@/components/ui/toast";
import { type AdminUserInput, saveAdminUser } from "@/lib/admin-users";
import { hasAdminRole } from "@/lib/auth";
import { getErrorMessage } from "@/lib/error";
import { m } from "@/paraglide/messages";
import { type SettingsUser, settingsUsersQueryKey } from "@/queries/settings";

const userSchema = (isEditing: boolean) =>
    v.object({
        username: v.pipe(
            v.string(),
            v.trim(),
            v.minLength(3, m.settings_validation_username()),
        ),
        email: v.pipe(
            v.string(),
            v.trim(),
            v.email(m.settings_validation_email()),
        ),
        name: v.pipe(
            v.string(),
            v.trim(),
            v.minLength(1, m.settings_validation_required()),
        ),
        password: isEditing
            ? v.union([
                  v.literal(""),
                  v.pipe(
                      v.string(),
                      v.minLength(8, m.settings_validation_password()),
                  ),
              ])
            : v.pipe(
                  v.string(),
                  v.minLength(8, m.settings_validation_password()),
              ),
        administrator: v.boolean(),
    });

export function UserFormDialog({
    user,
    onClose,
}: {
    user: SettingsUser | null;
    onClose: () => void;
}) {
    const queryClient = useQueryClient();
    const isEditing = user !== null;
    const saveMutation = useMutation({
        mutationFn: (value: AdminUserInput) => saveAdminUser(user, value),
        onSuccess: async (result) => {
            await queryClient.invalidateQueries({
                queryKey: settingsUsersQueryKey,
            });
            if (result.status === "profile-saved") {
                toast.add({
                    id: "settings-user-partially-saved",
                    type: "warning",
                    title: m.settings_user_partially_saved(),
                    description: m.settings_user_password_save_error(),
                    priority: "high",
                });
                onClose();
                return;
            }
            toast.add({
                id: isEditing ? "settings-user-saved" : "settings-user-created",
                type: "success",
                title: isEditing
                    ? m.settings_user_saved()
                    : m.settings_user_created(),
            });
            onClose();
        },
        onError: (error) =>
            toast.add({
                id: "settings-user-save-error",
                type: "error",
                title: isEditing
                    ? m.settings_user_save_error()
                    : m.settings_user_create_error(),
                description: getErrorMessage(error),
                priority: "high",
            }),
    });
    const form = useForm({
        defaultValues: {
            username: user?.username ?? "",
            email: user?.email ?? "",
            name: user?.name ?? "",
            password: "",
            administrator: hasAdminRole(user?.role),
        },
        validators: {
            onChange: userSchema(isEditing),
            onSubmit: userSchema(isEditing),
        },
        onSubmit: ({ value }) => saveMutation.mutate(value),
    });

    return (
        <Dialog open onOpenChange={(open) => !open && onClose()}>
            <DialogContent className="sm:max-w-lg">
                <DialogHeader>
                    <DialogTitle>
                        {isEditing
                            ? m.settings_edit_user()
                            : m.settings_new_user()}
                    </DialogTitle>
                    <DialogDescription>
                        {isEditing
                            ? m.settings_edit_user_description()
                            : m.settings_create_user_description()}
                    </DialogDescription>
                </DialogHeader>
                <form
                    className="grid gap-4"
                    onSubmit={(event) => {
                        event.preventDefault();
                        void form.handleSubmit();
                    }}
                >
                    <div className="grid gap-4 sm:grid-cols-2">
                        <form.Field name="username">
                            {(field) => (
                                <Field data-invalid={!field.state.meta.isValid}>
                                    <FieldLabel htmlFor={field.name}>
                                        {m.settings_username()}
                                    </FieldLabel>
                                    <Input
                                        id={field.name}
                                        value={field.state.value}
                                        onBlur={field.handleBlur}
                                        onChange={(event) =>
                                            field.handleChange(
                                                event.target.value,
                                            )
                                        }
                                        aria-invalid={!field.state.meta.isValid}
                                        autoComplete="username"
                                    />
                                    <FieldError
                                        errors={field.state.meta.errors}
                                    />
                                </Field>
                            )}
                        </form.Field>
                        <form.Field name="email">
                            {(field) => (
                                <Field data-invalid={!field.state.meta.isValid}>
                                    <FieldLabel htmlFor={field.name}>
                                        {m.settings_email()}
                                    </FieldLabel>
                                    <Input
                                        id={field.name}
                                        type="email"
                                        value={field.state.value}
                                        onBlur={field.handleBlur}
                                        onChange={(event) =>
                                            field.handleChange(
                                                event.target.value,
                                            )
                                        }
                                        aria-invalid={!field.state.meta.isValid}
                                        autoComplete="email"
                                    />
                                    <FieldError
                                        errors={field.state.meta.errors}
                                    />
                                </Field>
                            )}
                        </form.Field>
                    </div>
                    <form.Field name="name">
                        {(field) => (
                            <Field data-invalid={!field.state.meta.isValid}>
                                <FieldLabel htmlFor={field.name}>
                                    {m.settings_name()}
                                </FieldLabel>
                                <Input
                                    id={field.name}
                                    value={field.state.value}
                                    onBlur={field.handleBlur}
                                    onChange={(event) =>
                                        field.handleChange(event.target.value)
                                    }
                                    aria-invalid={!field.state.meta.isValid}
                                    autoComplete="name"
                                />
                                <FieldError errors={field.state.meta.errors} />
                            </Field>
                        )}
                    </form.Field>
                    <form.Field name="password">
                        {(field) => (
                            <Field data-invalid={!field.state.meta.isValid}>
                                <FieldLabel htmlFor={field.name}>
                                    {isEditing
                                        ? m.settings_new_password()
                                        : m.settings_password()}
                                </FieldLabel>
                                <Input
                                    id={field.name}
                                    type="password"
                                    value={field.state.value}
                                    onBlur={field.handleBlur}
                                    onChange={(event) =>
                                        field.handleChange(event.target.value)
                                    }
                                    placeholder={
                                        isEditing
                                            ? m.settings_password_unchanged()
                                            : undefined
                                    }
                                    aria-invalid={!field.state.meta.isValid}
                                    autoComplete="new-password"
                                />
                                <FieldError errors={field.state.meta.errors} />
                            </Field>
                        )}
                    </form.Field>
                    <form.Field name="administrator">
                        {(field) => (
                            <Field orientation="horizontal">
                                <div className="flex-1">
                                    <FieldLabel htmlFor={field.name}>
                                        {m.settings_administrator()}
                                    </FieldLabel>
                                    <p className="text-sm text-muted-foreground">
                                        {m.settings_administrator_description()}
                                    </p>
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
                            disabled={saveMutation.isPending}
                        >
                            {m.settings_cancel()}
                        </Button>
                        <form.Subscribe selector={(state) => state.canSubmit}>
                            {(canSubmit) => (
                                <Button
                                    type="submit"
                                    disabled={
                                        !canSubmit || saveMutation.isPending
                                    }
                                >
                                    {saveMutation.isPending && <Spinner />}
                                    {isEditing
                                        ? m.settings_save_changes()
                                        : m.settings_create_user()}
                                </Button>
                            )}
                        </form.Subscribe>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    );
}
