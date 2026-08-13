const configuredApiUrl = import.meta.env.VITE_API_URL ?? "/api";

export const apiUrl = new URL(configuredApiUrl, window.location.origin)
    .toString()
    .replace(/\/$/, "");
