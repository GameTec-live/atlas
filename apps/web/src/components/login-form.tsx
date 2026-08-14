import { useRouter } from "@tanstack/react-router";
import { type SubmitEvent, useState } from "react";
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
                <svg
                    xmlns="http://www.w3.org/2000/svg"
                    viewBox="0 0 24 24"
                    className="w-full overflow-visible"
                >
                    <title>Logo</title>
                    <path d="M17.36,2.64L15.95,4.06C17.26,5.37 18,7.14 18,9A7,7 0 0,1 11,16C9.15,16 7.37,15.26 6.06,13.95L4.64,15.36C6.08,16.8 7.97,17.71 10,17.93V20H6V22H16V20H12V17.94C16.55,17.43 20,13.58 20,9C20,6.62 19.05,4.33 17.36,2.64" />
                    <g
                        className={cn(
                            "transform-gpu origin-[11px_9px]",
                            logoState === "submitting" && "animate-logo-turn",
                            logoState === "error" && "animate-logo-fall",
                        )}
                    >
                        <path d="M11,3.5A5.5,5.5 0 0,0 5.5,9A5.5,5.5 0 0,0 11,14.5A5.5,5.5 0 0,0 16.5,9A5.5,5.5 0 0,0 11,3.5M11,5.5C12.94,5.5 14.5,7.07 14.5,9A3.5,3.5 0 0,1 11,12.5A3.5,3.5 0 0,1 7.5,9A3.5,3.5 0 0,1 11,5.5Z" />
                    </g>
                </svg>
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
