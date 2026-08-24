import { Value } from "@sinclair/typebox/value";
import { env } from "@/env";
import { config } from "../config";
import { GeoservicesModel } from "./model";

export interface RoutePoint {
    latitude: number;
    longitude: number;
    heading?: number;
}

export async function requestRoute(
    points: RoutePoint[],
    language = config.routing.defaultLanguage,
) {
    const routerUrl = env.ROUTER_URL.replace(/\/+$/, "");
    const routeQuery = {
        locations: points.map((point) => ({
            options: {
                allowUTurn: false,
            },
            latLng: {
                lat: point.latitude,
                lng: point.longitude,
            },
            _initHooksCalled: true,
            lat: point.latitude,
            lon: point.longitude,
            ...(point.heading === undefined ? {} : { heading: point.heading }),
        })),
        costing: "auto",
        directions_options: {
            language,
        },
    };

    const response = await fetch(
        `${routerUrl}/route?json=${encodeURIComponent(JSON.stringify(routeQuery))}`,
        {
            method: "GET",
        },
    );

    return {
        status: response.status,
        result: Value.Decode(
            GeoservicesModel.routeResponse,
            await response.json(),
        ),
    };
}
