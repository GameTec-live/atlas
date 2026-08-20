import { Marker } from "react-map-gl/maplibre";
import { Badge } from "@/components/ui/badge";
import {
    Tooltip,
    TooltipContent,
    TooltipTrigger,
} from "@/components/ui/tooltip";
import type { LiveDriver } from "@/hooks/use-live-drivers";
import { m } from "@/paraglide/messages";

const markerColors = {
    free: "bg-emerald-500",
    onTheWay: "bg-blue-500",
    occupied: "bg-amber-500",
    away: "bg-slate-400",
} satisfies Record<LiveDriver["state"], string>;

const stateLabels = {
    free: m.dizzy_silly_gopher_boil(),
    onTheWay: m.fit_mild_halibut_grace(),
    occupied: m.inclusive_bright_halibut_flop(),
    away: m.small_house_grizzly_view(),
} satisfies Record<LiveDriver["state"], string>;

export function DriverMarker({ driver }: { driver: LiveDriver }) {
    return (
        <Marker
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

                    {driver.fuelLevel !== undefined ||
                    driver.odometer !== undefined ? (
                        <dl className="grid grid-cols-2 gap-2 border-muted-foreground border-t pt-2">
                            {driver.fuelLevel !== undefined && (
                                <>
                                    <dt>{m.extra_still_giraffe_fetch()}</dt>
                                    <dd className="text-right tabular-nums">
                                        {driver.fuelLevel}%
                                    </dd>
                                </>
                            )}

                            {driver.odometer !== undefined && (
                                <>
                                    <dt>{m.happy_flat_kudu_stop()}</dt>
                                    <dd className="text-right tabular-nums">
                                        {driver.odometer} km
                                    </dd>
                                </>
                            )}
                        </dl>
                    ) : null}
                </TooltipContent>
            </Tooltip>
        </Marker>
    );
}
