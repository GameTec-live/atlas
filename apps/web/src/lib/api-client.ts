import { treaty } from "@elysia/eden";
import type { App } from "api";
import { apiUrl } from "./api-url";

export const api = treaty<App>(apiUrl, {
    // TanStack Query expects failed requests to reject.
    throwHttpError: true,
});

type EdenResponse<T> =
    | { data: T; error: null }
    | { data: null; error: unknown };

/** Extracts Eden response data while preserving its inferred type. */
export async function unwrapEden<T>(request: Promise<EdenResponse<T>>) {
    const response = await request;
    if (response.error !== null) throw response.error;
    return response.data;
}
