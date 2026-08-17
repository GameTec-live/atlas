import { TanStackDevtools } from "@tanstack/react-devtools";
import { ReactQueryDevtoolsPanel } from "@tanstack/react-query-devtools";
import { createRootRouteWithContext, Outlet } from "@tanstack/react-router";
import { TanStackRouterDevtoolsPanel } from "@tanstack/react-router-devtools";

import "../index.css";
import NotFound from "@/components/404";
import { ThemeProvider } from "@/components/theme-provider";
import { TooltipProvider } from "@/components/ui/tooltip";
import type { RouterContext } from "@/router";

export const Route = createRootRouteWithContext<RouterContext>()({
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
