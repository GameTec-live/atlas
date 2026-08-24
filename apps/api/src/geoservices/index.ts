import { Value } from "@sinclair/typebox/value";
import { eq } from "drizzle-orm";
import { Elysia } from "elysia";
import { env } from "@/env";
import { authHandler } from "../authHandler";
import { db } from "../db";
import { shortname } from "../db/schema";
import { GEOCODER_TIMEOUT_MS, requestReverseGeocode } from "./geocoder";
import { type GeocoderResponse, GeoservicesModel } from "./model";
import { requestRoute } from "./router";

const CACHE_SIZE = 1000;
const GEOCODER_URL = env.GEOCODER_URL.replace(/\/+$/, "");

interface ResolveCacheEntry {
    resolvedAddress: string;
    response: GeocoderResponse;
}

const resolveCache = new Map<string, ResolveCacheEntry>();
const pendingRevalidations = new Map<string, Promise<void>>();

const resolveShortname = async (address: string) => {
    const result = await db
        .select()
        .from(shortname)
        .where(eq(shortname.key, address.trim().toLowerCase()))
        .limit(1);

    return result[0]?.value ?? address;
};

const revalidateCachedResult = (
    address: string,
    cachedEntry: ResolveCacheEntry,
) => {
    if (pendingRevalidations.has(address)) return;

    const revalidation = resolveShortname(address)
        .then((resolvedAddress) => {
            if (
                resolvedAddress !== cachedEntry.resolvedAddress &&
                resolveCache.get(address) === cachedEntry
            ) {
                resolveCache.delete(address);
            }
        })
        .catch(() => {
            // temporary database error must not invalidate cache
        })
        .finally(() => {
            if (pendingRevalidations.get(address) === revalidation) {
                pendingRevalidations.delete(address);
            }
        });

    pendingRevalidations.set(address, revalidation);
};

export const geoservices = new Elysia({
    prefix: "/geoservices",
    tags: ["geoservices"],
})
    .use(authHandler)
    .get(
        "/resolve",
        async ({ query }) => {
            // Check cache
            const cachedResult = resolveCache.get(query.address);

            if (cachedResult !== undefined) {
                resolveCache.delete(query.address);
                resolveCache.set(query.address, cachedResult);
                revalidateCachedResult(query.address, cachedResult);
                return cachedResult.response;
            }

            // Resolve shortnames
            const value = await resolveShortname(query.address);

            // Geocode
            const geocodeResponse = await fetch(
                `${GEOCODER_URL}/geocode?q=${encodeURIComponent(value)}`,
                {
                    method: "GET",
                    signal: AbortSignal.timeout(GEOCODER_TIMEOUT_MS),
                },
            );

            const geocoderResult = Value.Decode(
                GeoservicesModel.geocoderResponse,
                await geocodeResponse.json(),
            );

            // Cache
            resolveCache.set(query.address, {
                resolvedAddress: value,
                response: geocoderResult,
            });

            if (resolveCache.size > CACHE_SIZE) {
                const oldestQuery = resolveCache.keys().next().value;
                if (oldestQuery !== undefined) resolveCache.delete(oldestQuery);
            }

            return geocoderResult;
        },
        {
            auth: true,
            query: GeoservicesModel.resolveQuery,
            response: GeoservicesModel.geocoderResponse,
        },
    )
    .get(
        "/reverse",
        async ({ query, set }) => {
            const { status, result } = await requestReverseGeocode(
                [query.lat, query.lon],
                {
                    radius_m: query.radius_m,
                    limit: query.limit,
                },
            );

            set.status = status;
            return result;
        },
        {
            auth: true,
            query: GeoservicesModel.reverseQuery,
            response: GeoservicesModel.reverseGeocoderResponse,
        },
    )
    .get(
        "/route",
        async ({ query, set }) => {
            const { status, result } = await requestRoute(
                [
                    {
                        latitude: query.fromlat,
                        longitude: query.fromlon,
                        heading: query.heading,
                    },
                    {
                        latitude: query.tolat,
                        longitude: query.tolon,
                    },
                ],
                query.lang,
            );

            set.status = status;
            return result;
        },
        {
            auth: true,
            query: GeoservicesModel.routeQuery,
            response: GeoservicesModel.routeResponse,
        },
    );
