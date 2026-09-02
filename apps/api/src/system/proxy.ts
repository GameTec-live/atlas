import type { Context } from "elysia";

type ResponseContext = {
    status: unknown;
    set: Context["set"];
};

// Native Responses hide their status from Elysia, which otherwise infers an
// unresolvable `200: Response` type for endpoints that actually return 202.
export const forwardJSON = async (
    responsePromise: Promise<Response>,
    { status, set }: ResponseContext,
) => {
    const response = await responsePromise;
    const cacheControl = response.headers.get("cache-control");
    if (cacheControl) set.headers["cache-control"] = cacheControl;

    const body: unknown = await response.json();
    return (status as Context["status"])(response.status, body);
};
