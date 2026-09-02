/** Turns API state identifiers such as `not_provisioned` into display text. */
export const formatSystemState = (state: string) => state.replaceAll("_", " ");

/** Parses comma- or newline-separated network values. */
export const parseSystemList = (value: string) =>
    value
        .split(/[\n,]/)
        .map((entry) => entry.trim())
        .filter(Boolean);

export type SystemIpMethod = "auto" | "manual" | "disabled";

/** Normalizes the looser method string returned by NetworkManager. */
export const getSystemIpMethod = (method: string): SystemIpMethod => {
    if (method === "manual" || method === "disabled") return method;
    return "auto";
};

/** Keeps a platform-specific current timezone selectable. */
export const getTimezoneOptions = (current: string) => {
    const supported = Intl.supportedValuesOf("timeZone");
    return supported.includes(current) ? supported : [current, ...supported];
};
