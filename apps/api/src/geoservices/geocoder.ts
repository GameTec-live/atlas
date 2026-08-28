import type { Static } from "@sinclair/typebox";
import { Value } from "@sinclair/typebox/value";
import { env } from "@/env";
import { GeoservicesModel } from "./model";

const GEOCODER_URL = env.GEOCODER_URL.replace(/\/+$/, "");
export const GEOCODER_TIMEOUT_MS = 30_000;
export const REVERSE_GEOCODER_CACHE_SIZE = 10_000;

interface ReverseGeocodeOptions {
    radius_m?: number;
    limit?: number;
}

type ReverseGeocodeResult = {
    status: number;
    result: Static<typeof GeoservicesModel.reverseGeocoderResponse>;
};

const reverseGeocodeCache = new Map<string, ReverseGeocodeResult>();

export const clearReverseGeocodeCache = () => reverseGeocodeCache.clear();

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

    const cacheKey = query.toString();
    const cachedResult = reverseGeocodeCache.get(cacheKey);
    if (cachedResult !== undefined) {
        reverseGeocodeCache.delete(cacheKey);
        reverseGeocodeCache.set(cacheKey, cachedResult);
        return cachedResult;
    }

    const response = await fetch(`${GEOCODER_URL}/reverse?${query}`, {
        signal: AbortSignal.timeout(GEOCODER_TIMEOUT_MS),
    });
    const result = Value.Decode(
        GeoservicesModel.reverseGeocoderResponse,
        await response.json(),
    );

    const geocodeResult = { status: response.status, result };
    reverseGeocodeCache.set(cacheKey, geocodeResult);

    if (reverseGeocodeCache.size > REVERSE_GEOCODER_CACHE_SIZE) {
        const oldestKey = reverseGeocodeCache.keys().next().value;
        if (oldestKey !== undefined) reverseGeocodeCache.delete(oldestKey);
    }

    return geocodeResult;
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
