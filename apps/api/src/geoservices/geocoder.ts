import { Value } from "@sinclair/typebox/value";
import { env } from "@/env";
import { GeoservicesModel } from "./model";

const GEOCODER_URL = env.GEOCODER_URL.replace(/\/+$/, "");
export const GEOCODER_TIMEOUT_MS = 30_000;

interface ReverseGeocodeOptions {
    radius_m?: number;
    limit?: number;
}

export const requestReverseGeocode = async (
    [latitude, longitude]: readonly [number, number],
    options: ReverseGeocodeOptions = {},
) => {
    const query = new URLSearchParams({
        lat: String(latitude),
        lon: String(longitude),
    });
    if (options.radius_m !== undefined) {
        query.set("radius_m", String(options.radius_m));
    }
    query.set("limit", String(options.limit ?? 1));

    const response = await fetch(`${GEOCODER_URL}/reverse?${query}`, {
        signal: AbortSignal.timeout(GEOCODER_TIMEOUT_MS),
    });
    const result = Value.Decode(
        GeoservicesModel.reverseGeocoderResponse,
        await response.json(),
    );

    return { status: response.status, result };
};

export const reverseGeocode = async (
    coordinates: readonly [number, number],
) => {
    const { result } = await requestReverseGeocode(coordinates);

    if ("error" in result) {
        throw new Error(
            `Reverse geocoding failed (${result.error.code}): ${result.error.message}`,
        );
    }

    return result.results[0]?.display_name;
};
