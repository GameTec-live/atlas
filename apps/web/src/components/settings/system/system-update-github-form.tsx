import { useForm } from "@tanstack/react-form";
import { useMutation } from "@tanstack/react-query";
import { GitPullRequestIcon } from "lucide-react";
import * as v from "valibot";
import { Button } from "@/components/ui/button";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { m } from "@/paraglide/messages";
import { systemGithubUpdateMutationOptions } from "@/queries/system";

const repositorySchema = v.object({
    repository: v.pipe(
        v.string(),
        v.regex(
            /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/,
            "Use the owner/repository format.",
        ),
    ),
});

export function SystemUpdateGithubForm({
    onSuccess,
    onError,
}: {
    onSuccess: () => void;
    onError: (error: unknown) => void;
}) {
    const mutation = useMutation({
        ...systemGithubUpdateMutationOptions(),
        onSuccess,
        onError,
    });
    const form = useForm({
        defaultValues: { repository: "GameTec-live/atlas" },
        validators: {
            onChange: repositorySchema,
            onSubmit: repositorySchema,
        },
        onSubmit: ({ value }) => mutation.mutate(value.repository),
    });

    return (
        <form
            className="grid gap-3"
            onSubmit={(event) => {
                event.preventDefault();
                void form.handleSubmit();
            }}
        >
            <form.Field name="repository">
                {(field) => (
                    <Field data-invalid={!field.state.meta.isValid}>
                        <FieldLabel htmlFor={field.name}>
                            {m.settings_system_github_repository()}
                        </FieldLabel>
                        <Input
                            id={field.name}
                            value={field.state.value}
                            onBlur={field.handleBlur}
                            onChange={(event) =>
                                field.handleChange(event.target.value)
                            }
                            aria-invalid={!field.state.meta.isValid}
                        />
                        <FieldError errors={field.state.meta.errors} />
                    </Field>
                )}
            </form.Field>
            <form.Subscribe selector={(state) => state.canSubmit}>
                {(canSubmit) => (
                    <Button
                        type="submit"
                        disabled={!canSubmit || mutation.isPending}
                    >
                        {mutation.isPending ? (
                            <Spinner />
                        ) : (
                            <GitPullRequestIcon />
                        )}
                        {m.settings_system_install_latest()}
                    </Button>
                )}
            </form.Subscribe>
        </form>
    );
}
