import { Link } from "@tanstack/react-router";
import { ExternalLinkIcon } from "lucide-react";
import { dashboardViewTransitions } from "@/components/dashboard/dashboard-card-header";
import { LiveDriversMap } from "@/components/live-drivers-map";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { m } from "@/paraglide/messages";

export function MiniMapCard() {
    const linkLabel = m.dashboard_open_section({
        section: m.dashboard_map_title(),
    });

    return (
        <Card
            className="relative min-h-80 gap-0 py-0 xl:col-span-6 xl:min-h-0"
            data-dashboard-transition="map"
        >
            <CardContent className="absolute inset-0 px-0">
                <LiveDriversMap />
            </CardContent>
            <Button
                variant="secondary"
                size="icon"
                nativeButton={false}
                render={
                    <Link
                        to="/realtime"
                        viewTransition={dashboardViewTransitions.map}
                    />
                }
                className="absolute top-3 right-3 z-10 bg-background/50 shadow-md backdrop-blur"
                aria-label={linkLabel}
                title={linkLabel}
            >
                <ExternalLinkIcon />
            </Button>
        </Card>
    );
}
