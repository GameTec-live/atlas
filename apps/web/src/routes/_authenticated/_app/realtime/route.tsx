import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowRight } from "lucide-react";
import MapLibre, { Marker } from "react-map-gl/maplibre";
import "maplibre-gl/dist/maplibre-gl.css";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
    Tooltip,
    TooltipContent,
    TooltipTrigger,
} from "@/components/ui/tooltip";
import { type LiveDriver, useLiveDrivers } from "@/hooks/use-live-drivers";

const markerColors = {
    free: "bg-emerald-500",
    onTheWay: "bg-blue-500",
    occupied: "bg-amber-500",
    away: "bg-slate-400",
} satisfies Record<LiveDriver["state"], string>;

const stateLabels = {
    free: "Free",
    onTheWay: "On the way",
    occupied: "Occupied",
    away: "Away",
} satisfies Record<LiveDriver["state"], string>;

export const Route = createFileRoute("/_authenticated/_app/realtime")({
    component: RealtimeMap,
});

function RealtimeMap() {
    const drivers = useLiveDrivers();
    const firstDriver = drivers.values().next().value;

    return (
        <div className="h-full">
            <MapLibre
                key={firstDriver ? "driver-available" : "no-drivers"}
                initialViewState={
                    firstDriver
                        ? {
                              longitude: firstDriver.longitude,
                              latitude: firstDriver.latitude,
                              zoom: 14,
                          }
                        : undefined
                }
                mapStyle="/map/style/liberty"
                attributionControl={false}
            >
                {[...drivers.values()].map((driver) => (
                    <Marker
                        key={driver.userId}
                        longitude={driver.longitude}
                        latitude={driver.latitude}
                        anchor="bottom"
                    >
                        <Tooltip>
                            <TooltipTrigger
                                render={
                                    <div
                                        className="flex items-center gap-2 rounded-full border bg-background/50 py-1 pr-2 pl-1 shadow-md backdrop-blur"
                                        title={driver.userName}
                                    >
                                        <span
                                            className={`size-3 rounded-full ${markerColors[driver.state]}`}
                                        />
                                        <span className="max-w-36 truncate text-xs font-medium">
                                            {driver.userName}
                                        </span>
                                    </div>
                                }
                            />
                            <TooltipContent className="flex-col items-stretch gap-2 p-2">
                                <div className="flex items-center justify-between gap-3">
                                    <p className="truncate font-semibold">
                                        {driver.userName}
                                    </p>
                                    <Badge>{stateLabels[driver.state]}</Badge>
                                </div>

                                <dl className="grid grid-cols-2 gap-2 border-muted-foreground border-t pt-2">
                                    {driver.fuelLevel !== undefined && (
                                        <>
                                            <dt>Fuel</dt>
                                            <dd className="text-right tabular-nums">
                                                {driver.fuelLevel}%
                                            </dd>
                                        </>
                                    )}

                                    {driver.odometer !== undefined && (
                                        <>
                                            <dt>Odometer</dt>
                                            <dd className="text-right tabular-nums">
                                                {driver.odometer} km
                                            </dd>
                                        </>
                                    )}
                                </dl>
                            </TooltipContent>
                        </Tooltip>
                    </Marker>
                ))}
            </MapLibre>

            <Button
                className="absolute right-4 bottom-4 z-10 h-10 px-4 shadow-lg"
                nativeButton={false}
                render={<Link to="/" />}
            >
                New job
                <ArrowRight data-icon="inline-end" />
            </Button>
        </div>
    );
}
