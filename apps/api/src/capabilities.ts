import { env } from "@/env";

/** Static deployment features that are safe to expose in bootstrap responses. */
export const getDeploymentCapabilities = () => ({
    systemManagement: env.OS_MANAGEMENT_SOCKET !== undefined,
});
