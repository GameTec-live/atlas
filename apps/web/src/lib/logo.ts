import { api, unwrapEden } from "@/lib/api-client";
import { apiUrl } from "@/lib/api-url";

const MAX_LOGO_SIZE = 5 * 1024 * 1024;

export async function uploadLogo(file: File) {
    if (!file.type.startsWith("image/")) {
        throw new Error("Unsupported logo image type");
    }
    if (file.size > MAX_LOGO_SIZE) {
        throw new Error("Logo is too large");
    }

    const response = await fetch(`${apiUrl}/config/logo`, {
        method: "PUT",
        credentials: "include",
        headers: { "content-type": file.type },
        body: file,
    });
    if (!response.ok) throw new Error(await response.text());
}

export const deleteLogo = () => unwrapEden(api.config.logo.delete());
