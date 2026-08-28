import { TanStackDevtools } from "@tanstack/react-devtools";
import { ReactQueryDevtoolsPanel } from "@tanstack/react-query-devtools";
import {
    createRootRouteWithContext,
    Outlet,
    redirect,
} from "@tanstack/react-router";
import { TanStackRouterDevtoolsPanel } from "@tanstack/react-router-devtools";

import "../index.css";
import NotFound from "@/components/404";
import { ThemeProvider } from "@/components/theme-provider";
import { Toaster } from "@/components/ui/toast";
import { TooltipProvider } from "@/components/ui/tooltip";
import { setupStatusQueryOptions } from "@/queries/setup";
import type { RouterContext } from "@/router";

export const Route = createRootRouteWithContext<RouterContext>()({
    beforeLoad: async ({ context, location }) => {
        const setup = await context.queryClient.ensureQueryData(
            setupStatusQueryOptions(),
        );
        if (!setup) throw new Error("The setup service returned no status.");

        if (!setup.complete && location.pathname !== "/setup") {
            throw redirect({
                to: "/setup",
                search: { step: "greeting" },
                replace: true,
            });
        }
        if (setup.complete && location.pathname === "/setup") {
            throw redirect({ to: "/", replace: true });
        }
    },
    component: RootComponent,
    notFoundComponent: NotFound,
});

function RootComponent() {
    return (
        <>
            <ThemeProvider>
                <TooltipProvider>
                    <Outlet />
                </TooltipProvider>
                <Toaster />
            </ThemeProvider>
            <TanStackDevtools
                config={{
                    position: "bottom-left",
                }}
                plugins={[
                    {
                        name: "TanStack Query",
                        render: <ReactQueryDevtoolsPanel />,
                    },
                    {
                        name: "TanStack Router",
                        render: <TanStackRouterDevtoolsPanel />,
                    },
                ]}
            />
        </>
    );
}
