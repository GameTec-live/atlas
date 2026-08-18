import { createFileRoute } from "@tanstack/react-router";
import { m } from "@/paraglide/messages";

export const Route = createFileRoute("/_authenticated/_app/settings/")({
    component: Settings,
});

function Settings() {
    return (
        <main className="mx-auto w-full max-w-4xl p-6 sm:p-10">
            <h1 className="text-2xl font-semibold tracking-tight">
                {m.factual_happy_falcon_arise()}
            </h1>
        </main>
    );
}
