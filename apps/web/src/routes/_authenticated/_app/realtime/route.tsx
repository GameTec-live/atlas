import { createFileRoute } from "@tanstack/react-router";
import { setWorkerUrl } from "maplibre-gl";
import maplibreWorkerUrl from "maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url";
import MapLibre from "react-map-gl/maplibre";
import "maplibre-gl/dist/maplibre-gl.css";
import { DriverMarker } from "@/components/driver-marker";
import { NewJobButton } from "@/components/new-job-button";
import { useLiveDrivers } from "@/hooks/use-live-drivers";

setWorkerUrl(maplibreWorkerUrl);

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
                    <DriverMarker key={driver.userId} driver={driver} />
                ))}
            </MapLibre>

            <NewJobButton className="absolute right-4 bottom-4 z-10 shadow-lg" />
        </div>
    );
}
