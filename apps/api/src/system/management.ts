import { env } from "@/env";

const DEFAULT_SOCKET = "/run/atlas-management/api.sock";
const DEFAULT_TOKEN_FILE = "/run/secrets/atlas-management-token";

export const UNAVAILABLE_MESSAGE =
    "Atlas OS management is unavailable. This is expected when Atlas is not running on Atlas OS.";

const socketPath = env.OS_MANAGEMENT_SOCKET ?? DEFAULT_SOCKET;
const tokenFile = env.OS_MANAGEMENT_TOKEN_FILE ?? DEFAULT_TOKEN_FILE;

export const request = async (path: string, init: RequestInit = {}) => {
    try {
        const token = (await Bun.file(tokenFile).text()).trim();
        if (!token) throw new Error("the management token is empty");

        const headers = new Headers(init.headers);
        headers.set("authorization", `Bearer ${token}`);

        return await fetch(`http://localhost${path}`, {
            ...init,
            headers,
            unix: socketPath,
        });
    } catch {
        return Response.json(
            {
                error: {
                    code: "management_unavailable",
                    message: UNAVAILABLE_MESSAGE,
                },
            },
            { status: 503, headers: { "cache-control": "no-store" } },
        );
    }
};

export const applyUpdate = async (path: string) => {
    const form = new FormData();
    form.set("bundle", Bun.file(path), "atlas-update.tar.zst");
    return request("/api/v1/update", { method: "POST", body: form });
};
