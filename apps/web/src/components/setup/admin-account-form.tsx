import { useForm } from "@tanstack/react-form";
import { useMutation } from "@tanstack/react-query";
import { CheckCircle2Icon } from "lucide-react";
import * as v from "valibot";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { getErrorMessage } from "@/lib/error";
import { m } from "@/paraglide/messages";
import { setupAdminMutationOptions } from "@/queries/setup";

const adminSchema = v.pipe(
    v.object({
        email: v.pipe(
            v.string(),
            v.trim(),
            v.email(m.setup_validation_email()),
        ),
        username: v.pipe(
            v.string(),
            v.trim(),
            v.minLength(3, m.setup_validation_username()),
        ),
        password: v.pipe(
            v.string(),
            v.minLength(8, m.setup_validation_password()),
        ),
        repeatPassword: v.string(),
    }),
    v.forward(
        v.partialCheck(
            [["password"], ["repeatPassword"]],
            ({ password, repeatPassword }) => password === repeatPassword,
            m.setup_validation_password_match(),
        ),
        ["repeatPassword"],
    ),
);

export function AdminAccountForm({
    isComplete,
    onComplete,
}: {
    isComplete: boolean;
    onComplete: () => Promise<void>;
}) {
    const mutation = useMutation({
        ...setupAdminMutationOptions(),
        onSuccess: onComplete,
    });
    const form = useForm({
        defaultValues: {
            email: "",
            username: "",
            password: "",
            repeatPassword: "",
        },
        validators: { onChange: adminSchema, onSubmit: adminSchema },
        onSubmit: ({ value }) =>
            mutation.mutate({
                email: value.email.trim(),
                username: value.username.trim(),
                password: value.password,
            }),
    });

    if (isComplete) {
        return (
            <Card className="mx-auto w-full max-w-lg">
                <CardContent className="flex items-center gap-4 py-4">
                    <span className="grid size-11 shrink-0 place-items-center rounded-full bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
                        <CheckCircle2Icon className="size-6" />
                    </span>
                    <div>
                        <p className="font-medium">
                            {m.setup_admin_created_title()}
                        </p>
                        <p className="text-sm text-muted-foreground">
                            {m.setup_admin_created_description()}
                        </p>
                    </div>
                </CardContent>
            </Card>
        );
    }

    return (
        <Card className="mx-auto w-full max-w-lg shadow-lg shadow-black/5">
            <CardContent className="py-2">
                <form
                    className="grid gap-5"
                    onSubmit={(event) => {
                        event.preventDefault();
                        void form.handleSubmit();
                    }}
                >
                    <form.Field name="email">
                        {(field) => (
                            <Field data-invalid={!field.state.meta.isValid}>
                                <FieldLabel htmlFor={field.name}>
                                    {m.setup_email()}
                                </FieldLabel>
                                <Input
                                    id={field.name}
                                    type="email"
                                    autoComplete="email"
                                    placeholder="admin@example.com"
                                    value={field.state.value}
                                    onBlur={field.handleBlur}
                                    onChange={(event) =>
                                        field.handleChange(event.target.value)
                                    }
                                    aria-invalid={!field.state.meta.isValid}
                                    disabled={mutation.isPending}
                                />
                                <FieldError errors={field.state.meta.errors} />
                            </Field>
                        )}
                    </form.Field>
                    <form.Field name="username">
                        {(field) => (
                            <Field data-invalid={!field.state.meta.isValid}>
                                <FieldLabel htmlFor={field.name}>
                                    {m.setup_username()}
                                </FieldLabel>
                                <Input
                                    id={field.name}
                                    autoComplete="username"
                                    placeholder="admin"
                                    value={field.state.value}
                                    onBlur={field.handleBlur}
                                    onChange={(event) =>
                                        field.handleChange(event.target.value)
                                    }
                                    aria-invalid={!field.state.meta.isValid}
                                    disabled={mutation.isPending}
                                />
                                <FieldError errors={field.state.meta.errors} />
                            </Field>
                        )}
                    </form.Field>
                    <div className="grid gap-5 sm:grid-cols-2">
                        <form.Field name="password">
                            {(field) => (
                                <Field data-invalid={!field.state.meta.isValid}>
                                    <FieldLabel htmlFor={field.name}>
                                        {m.setup_password()}
                                    </FieldLabel>
                                    <Input
                                        id={field.name}
                                        type="password"
                                        autoComplete="new-password"
                                        value={field.state.value}
                                        onBlur={field.handleBlur}
                                        onChange={(event) =>
                                            field.handleChange(
                                                event.target.value,
                                            )
                                        }
                                        aria-invalid={!field.state.meta.isValid}
                                        disabled={mutation.isPending}
                                    />
                                    <FieldError
                                        errors={field.state.meta.errors}
                                    />
                                </Field>
                            )}
                        </form.Field>
                        <form.Field name="repeatPassword">
                            {(field) => (
                                <Field data-invalid={!field.state.meta.isValid}>
                                    <FieldLabel htmlFor={field.name}>
                                        {m.setup_repeat_password()}
                                    </FieldLabel>
                                    <Input
                                        id={field.name}
                                        type="password"
                                        autoComplete="new-password"
                                        value={field.state.value}
                                        onBlur={field.handleBlur}
                                        onChange={(event) =>
                                            field.handleChange(
                                                event.target.value,
                                            )
                                        }
                                        aria-invalid={!field.state.meta.isValid}
                                        disabled={mutation.isPending}
                                    />
                                    <FieldError
                                        errors={field.state.meta.errors}
                                    />
                                </Field>
                            )}
                        </form.Field>
                    </div>
                    {mutation.isError && (
                        <Field data-invalid>
                            <FieldError>
                                {getErrorMessage(mutation.error)}
                            </FieldError>
                        </Field>
                    )}
                    <form.Subscribe selector={(state) => state.canSubmit}>
                        {(canSubmit) => (
                            <Button
                                type="submit"
                                size="lg"
                                className="mt-1 w-full"
                                disabled={!canSubmit || mutation.isPending}
                            >
                                {mutation.isPending && <Spinner />}
                                {m.setup_create_admin()}
                            </Button>
                        )}
                    </form.Subscribe>
                </form>
            </CardContent>
        </Card>
    );
}
