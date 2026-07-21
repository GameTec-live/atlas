import { Value } from "@sinclair/typebox/value";
import { eq } from "drizzle-orm";
import { Elysia } from "elysia";
import { env } from "@/env";
import { authHandler } from "../authHandler";
import { db } from "../db";
import { shortname } from "../db/schema";
import { type GeocoderResponse, GeoservicesModel } from "./model";

const CACHE_SIZE = 1000;
const GEOCODER_URL = env.GEOCODER_URL.replace(/\/+$/, "");
const ROUTER_URL = env.ROUTER_URL.replace(/\/+$/, "");

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
        .where(eq(shortname.key, address.toLowerCase()))
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
        "/route",
        async ({ query }) => {
            const routeQuery = {
                locations: [
                    {
                        options: {
                            allowUTurn: false,
                        },
                        latLng: {
                            lat: query.fromlat,
                            lng: query.fromlon,
                        },
                        _initHooksCalled: true,
                        lat: query.fromlat,
                        lon: query.fromlon,
                    },
                    {
                        options: {
                            allowUTurn: false,
                        },
                        latLng: {
                            lat: query.tolat,
                            lng: query.tolon,
                        },
                        _initHooksCalled: true,
                        lat: query.tolat,
                        lon: query.tolon,
                    },
                ],
                costing: "auto",
                directions_options: {
                    language: query.lang || "en-US", // TODO: Make default configurable once config file is implemented
                },
            };

            const routeResponse = await fetch(
                `${ROUTER_URL}/route?json=${encodeURIComponent(JSON.stringify(routeQuery))}`,
                {
                    method: "GET",
                },
            );
            return Value.Decode(
                GeoservicesModel.routeResponse,
                await routeResponse.json(),
            );
        },
        {
            auth: true,
            query: GeoservicesModel.routeQuery,
            response: GeoservicesModel.routeResponse,
        },
    );
