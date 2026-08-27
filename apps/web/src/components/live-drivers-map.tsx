import { setWorkerUrl } from "maplibre-gl";
import maplibreWorkerUrl from "maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url";
import MapLibre from "react-map-gl/maplibre";
import "maplibre-gl/dist/maplibre-gl.css";
import { DriverMarker } from "@/components/driver-marker";
import { useLiveDrivers } from "@/hooks/use-live-drivers";
import { cn } from "@/lib/utils";

setWorkerUrl(maplibreWorkerUrl);

/** The shared live fleet map used by both the realtime and dashboard pages. */
export function LiveDriversMap({ className }: { className?: string }) {
    const drivers = useLiveDrivers();
    const firstDriver = drivers.values().next().value;

    return (
        <div className={cn("h-full w-full", className)}>
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
        </div>
    );
}
