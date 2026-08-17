import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/_authenticated/_app/settings")({
    component: Settings,
});

function Settings() {
    return (
        <main>
            <h1>Settings</h1>
        </main>
    );
}
