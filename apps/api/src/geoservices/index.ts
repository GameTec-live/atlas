import { Value } from "@sinclair/typebox/value";
import { eq } from "drizzle-orm";
import { Elysia } from "elysia";
import { env } from "@/env";
import { authHandler } from "../authHandler";
import { db } from "../db";
import { shortname } from "../db/schema";
import { type GeocoderResponse, GeoservicesModel } from "./model";

const CACHE_SIZE = 1000;
const resolveCache = new Map<string, GeocoderResponse>();

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
                return cachedResult;
            }

            // Resolve shortnames
            const result = await db
                .select()
                .from(shortname)
                .where(eq(shortname.key, query.address.toLowerCase()))
                .limit(1);

            const value = result[0]?.value ?? query.address;

            // Geocode
            const geocodeResponse = await fetch(
                `${env.GEOCODER_URL}/geocode?q=${encodeURIComponent(value)}`,
                {
                    method: "GET",
                },
            );

            const geocoderResult = Value.Decode(
                GeoservicesModel.geocoderResponse,
                await geocodeResponse.json(),
            );

            // Cache
            resolveCache.set(query.address, geocoderResult);

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
    );
