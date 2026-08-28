import { RouteIcon } from "lucide-react";
import { LngLatBounds, setWorkerUrl } from "maplibre-gl";
import maplibreWorkerUrl from "maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url";
import { useEffect, useMemo, useState } from "react";
import MapLibre, {
    Layer,
    type MapRef,
    Marker,
    Source,
} from "react-map-gl/maplibre";
import "maplibre-gl/dist/maplibre-gl.css";
import { useQuery } from "@tanstack/react-query";
import { DriverMarker } from "@/components/driver-marker";
import { Badge } from "@/components/ui/badge";
import { Spinner } from "@/components/ui/spinner";
import { useLiveDrivers } from "@/hooks/use-live-drivers";
import { type Coordinates, decodeRouteShapes, toMapPoint } from "@/lib/route";
import { cn } from "@/lib/utils";
import { m } from "@/paraglide/messages";
import { routeQueryOptions } from "@/queries/geoservices";

setWorkerUrl(maplibreWorkerUrl);

type JobDetailsMapProps = {
    from: Coordinates | null;
    to: Coordinates | null;
};

export function JobDetailsMap({ from, to }: JobDetailsMapProps) {
    const [map, setMap] = useState<MapRef | null>(null);
    const drivers = useLiveDrivers();
    const routeQuery = useQuery(routeQueryOptions(from, to));
    const routeCoordinates = useMemo(() => {
        if (!routeQuery.data || !("trip" in routeQuery.data)) return [];
        return decodeRouteShapes(routeQuery.data.trip.legs);
    }, [routeQuery.data]);

    const routeGeoJson = useMemo(
        () => ({
            type: "FeatureCollection" as const,
            features:
                routeCoordinates.length > 1
                    ? [
                          {
                              type: "Feature" as const,
                              properties: {},
                              geometry: {
                                  type: "LineString" as const,
                                  coordinates: routeCoordinates,
                              },
                          },
                      ]
                    : [],
        }),
        [routeCoordinates],
    );

    const visiblePoints = useMemo(
        () =>
            routeCoordinates.length > 0
                ? routeCoordinates
                : [
                      ...(from ? [toMapPoint(from)] : []),
                      ...(to ? [toMapPoint(to)] : []),
                  ],
        [from, routeCoordinates, to],
    );

    useEffect(() => {
        if (!map || visiblePoints.length === 0) return;

        if (visiblePoints.length === 1) {
            map.easeTo({ center: visiblePoints[0], zoom: 14, duration: 700 });
            return;
        }

        const bounds = visiblePoints.reduce(
            (result, point) => result.extend(point),
            new LngLatBounds(),
        );
        map.fitBounds(bounds, {
            padding: { top: 200, right: 72, bottom: 72, left: 72 },
            maxZoom: 15,
            duration: 700,
        });
    }, [map, visiblePoints]);

    const routeFailed =
        routeQuery.isError ||
        Boolean(routeQuery.data && !("trip" in routeQuery.data));

    return (
        <>
            <MapLibre
                ref={setMap}
                initialViewState={
                    from
                        ? {
                              longitude: from[1],
                              latitude: from[0],
                              zoom: 13,
                          }
                        : undefined
                }
                mapStyle="/map/style/liberty"
                attributionControl={false}
                reuseMaps
            >
                <Source id="job-route" type="geojson" data={routeGeoJson}>
                    <Layer
                        id="job-route-casing"
                        type="line"
                        layout={{ "line-cap": "round", "line-join": "round" }}
                        paint={{
                            "line-color": "#ffffff",
                            "line-width": 10,
                            "line-opacity": 0.95,
                        }}
                    />
                    <Layer
                        id="job-route-line"
                        type="line"
                        layout={{ "line-cap": "round", "line-join": "round" }}
                        paint={{ "line-color": "#2563eb", "line-width": 6 }}
                    />
                </Source>

                {[...drivers.values()].map((driver) => (
                    <DriverMarker key={driver.userId} driver={driver} />
                ))}

                {from && (
                    <JobMarker
                        coordinates={from}
                        label={m.jobs_from()}
                        variant="from"
                    />
                )}
                {to && (
                    <JobMarker
                        coordinates={to}
                        label={m.jobs_to()}
                        variant="to"
                    />
                )}
            </MapLibre>

            <div className="absolute bottom-4 left-1/2 z-10 -translate-x-1/2">
                {routeQuery.isFetching && to && (
                    <Badge className="gap-2 bg-background/50 px-3 py-1.5 shadow-md backdrop-blur">
                        <Spinner />
                        {m.job_details_route_loading()}
                    </Badge>
                )}
                {routeFailed && (
                    <Badge className="gap-2 bg-background/50 px-3 py-1.5 shadow-md backdrop-blur">
                        <RouteIcon />
                        {m.job_details_route_unavailable()}
                    </Badge>
                )}
            </div>
        </>
    );
}

type JobMarkerProps = {
    coordinates: Coordinates;
    label: string;
    variant: "from" | "to";
};

export function JobMarker({ coordinates, label, variant }: JobMarkerProps) {
    return (
        <Marker
            longitude={coordinates[1]}
            latitude={coordinates[0]}
            anchor="bottom"
        >
            <div className="flex flex-col items-center drop-shadow-lg">
                <div className="flex items-center gap-2 rounded-full border bg-background/95 py-1.5 pr-3 pl-1.5 shadow-md backdrop-blur">
                    <span
                        className={cn(
                            "flex size-7 items-center justify-center rounded-full text-xs font-bold text-white",
                            variant === "from"
                                ? "bg-emerald-600"
                                : "bg-blue-600",
                        )}
                    >
                        {variant === "from" ? "A" : "B"}
                    </span>
                    <span className="text-xs font-semibold">{label}</span>
                </div>
                <span className="h-2 w-px bg-foreground/60" />
                <span
                    className={cn(
                        "size-2 rounded-full ring-2 ring-background",
                        variant === "from" ? "bg-emerald-600" : "bg-blue-600",
                    )}
                />
            </div>
        </Marker>
    );
}
