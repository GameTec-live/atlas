import { useRouter } from "@tanstack/react-router";
import { type SubmitEvent, useState } from "react";
import { AtlasLogo } from "@/components/atlas-logo";
import { Button } from "@/components/ui/button";
import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import {
    Field,
    FieldError,
    FieldGroup,
    FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { hasAdminRole } from "@/lib/auth";
import { authClient } from "@/lib/auth-client";
import { cn } from "@/lib/utils";
import { m } from "@/paraglide/messages";
import { Spinner } from "./ui/spinner";

export function LoginForm({
    redirectTo,
    initialError,
    className,
    ...props
}: React.ComponentProps<"div"> & {
    redirectTo: string;
    initialError?: string;
}) {
    const router = useRouter();
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [formError, setFormError] = useState(initialError);
    const [logoState, setLogoState] = useState<
        "idle" | "submitting" | "success" | "error"
    >("idle");

    async function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
        event.preventDefault();
        setFormError(undefined);
        setIsSubmitting(true);
        setLogoState("submitting");

        const formData = new FormData(event.currentTarget);
        const username = formData.get("username");
        const password = formData.get("password");

        if (typeof username !== "string" || typeof password !== "string") {
            setFormError("Enter your username and password.");
            setLogoState("error");
            setIsSubmitting(false);
            return;
        }

        try {
            const { data, error } = await authClient.signIn.username({
                username,
                password,
            });

            if (error) {
                setFormError(error.message ?? m.noble_lofty_otter_ripple());
                setLogoState("error");
                return;
            }

            if (!data || !hasAdminRole(data.user.role)) {
                await authClient.signOut();
                setFormError(m.admin_access_required());
                setLogoState("error");
                return;
            }

            setLogoState("success");
            await new Promise((resolve) => setTimeout(resolve, 150));
            router.history.replace(redirectTo);
        } catch {
            setFormError(m.sweet_sleek_weasel_savor());
            setLogoState("error");
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <div className={cn("flex flex-col gap-6", className)} {...props}>
            <div
                className={cn(
                    "mx-auto w-1/2 transform-gpu",
                    logoState === "success" && "animate-logo-happy",
                )}
            >
                <AtlasLogo
                    className="w-full overflow-visible dark:invert"
                    globeClassName={cn(
                        logoState === "submitting" && "animate-logo-turn",
                        logoState === "error" && "animate-logo-fall",
                    )}
                />
            </div>
            <Card>
                <CardHeader>
                    <CardTitle>{m.due_agent_reindeer_trust()}</CardTitle>
                    <CardDescription>
                        {m.house_bald_eagle_greet()}
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    <form onSubmit={handleSubmit}>
                        <FieldGroup>
                            <Field>
                                <FieldLabel htmlFor="username">
                                    {m.bland_formal_yak_climb()}
                                </FieldLabel>
                                <Input
                                    id="username"
                                    name="username"
                                    type="username"
                                    placeholder="johndoe"
                                    autoComplete="username"
                                    disabled={isSubmitting}
                                    required
                                />
                            </Field>
                            <Field>
                                <div className="flex items-center">
                                    <FieldLabel htmlFor="password">
                                        {m.sad_proud_orangutan_fall()}
                                    </FieldLabel>
                                </div>
                                <Input
                                    id="password"
                                    name="password"
                                    type="password"
                                    autoComplete="current-password"
                                    disabled={isSubmitting}
                                    required
                                />
                            </Field>
                            <Field data-invalid={formError !== undefined}>
                                <FieldError>{formError}</FieldError>
                            </Field>
                            <Field>
                                <Button type="submit" disabled={isSubmitting}>
                                    {isSubmitting ? (
                                        <Spinner />
                                    ) : (
                                        m.factual_fun_squid_fry()
                                    )}
                                </Button>
                            </Field>
                        </FieldGroup>
                    </form>
                </CardContent>
            </Card>
        </div>
    );
}
