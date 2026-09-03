import { mutationOptions, queryOptions } from "@tanstack/react-query";
import { api, unwrapEden } from "@/lib/api-client";
import { apiUrl } from "@/lib/api-url";
import { getErrorMessage } from "@/lib/error";
import type { SystemIpMethod } from "@/lib/system";

const keys = {
    info: ["api", "info"],
    containers: ["system", "containers"],
    update: ["system", "update"],
    ssh: ["system", "ssh"],
    timezone: ["system", "timezone"],
    connections: ["system", "connections"],
    devices: ["system", "devices"],
    remoteAccess: ["system", "remote-access"],
    authOrigins: ["system", "auth-origins"],
} as const;

export const systemUpdateQueryKey = keys.update;
export const systemSshQueryKey = keys.ssh;
export const systemTimezoneQueryKey = keys.timezone;
export const systemRemoteAccessQueryKey = keys.remoteAccess;
export const systemAuthOriginsQueryKey = keys.authOrigins;

const fetchApiInfo = () => unwrapEden(api.get());
const fetchContainers = () => unwrapEden(api.system.containers.get());
const fetchUpdate = () => unwrapEden(api.system.update.get());
const fetchSsh = () => unwrapEden(api.system.ssh.get());
const fetchTimezone = () => unwrapEden(api.system.timezone.get());
const fetchConnections = () =>
    unwrapEden(api.system.connections["network-manager"].get());
const fetchDevices = () =>
    unwrapEden(api.system.connections["network-manager"].devices.get());
const fetchRemoteAccess = () =>
    unwrapEden(api.system.connections["remote-access"].get());
const fetchAuthOrigins = () =>
    unwrapEden(api.system.connections["auth-origins"].get());
const fetchIpSettings = (uuid: string) =>
    unwrapEden(api.system.connections["network-manager"]({ uuid }).ip.get());

export type SystemContainers = NonNullable<
    Awaited<ReturnType<typeof fetchContainers>>
>;
export type ApiInfo = NonNullable<Awaited<ReturnType<typeof fetchApiInfo>>>;
export type SystemUpdate = NonNullable<Awaited<ReturnType<typeof fetchUpdate>>>;
export type SystemConnection = NonNullable<
    Awaited<ReturnType<typeof fetchConnections>>
>["items"][number];
export type SystemIpSettings = NonNullable<
    Awaited<ReturnType<typeof fetchIpSettings>>
>;
export type SystemRemoteAccess = NonNullable<
    Awaited<ReturnType<typeof fetchRemoteAccess>>
>;

export const apiInfoQueryOptions = () =>
    queryOptions({
        queryKey: keys.info,
        queryFn: fetchApiInfo,
        staleTime: Number.POSITIVE_INFINITY,
    });

export const systemContainersQueryOptions = () =>
    queryOptions({ queryKey: keys.containers, queryFn: fetchContainers });
export const systemUpdateQueryOptions = () =>
    queryOptions({ queryKey: keys.update, queryFn: fetchUpdate });
export const systemSshQueryOptions = () =>
    queryOptions({ queryKey: keys.ssh, queryFn: fetchSsh });
export const systemTimezoneQueryOptions = () =>
    queryOptions({ queryKey: keys.timezone, queryFn: fetchTimezone });
export const systemConnectionsQueryOptions = () =>
    queryOptions({ queryKey: keys.connections, queryFn: fetchConnections });
export const systemDevicesQueryOptions = () =>
    queryOptions({ queryKey: keys.devices, queryFn: fetchDevices });
export const systemRemoteAccessQueryOptions = () =>
    queryOptions({ queryKey: keys.remoteAccess, queryFn: fetchRemoteAccess });
export const systemAuthOriginsQueryOptions = () =>
    queryOptions({ queryKey: keys.authOrigins, queryFn: fetchAuthOrigins });

export const systemIpSettingsQueryOptions = (uuid?: string) =>
    queryOptions({
        queryKey: ["system", "connections", uuid, "ip"],
        queryFn: () => fetchIpSettings(uuid ?? ""),
        enabled: uuid !== undefined,
    });

export interface SystemIpSettingsInput {
    uuid: string;
    ipv4: IpFamilySettings;
    ipv6: IpFamilySettings;
}

interface IpFamilySettings {
    method: SystemIpMethod;
    addresses: string[];
    gateway?: string;
    dns: string[];
}

export const systemTimezoneMutationOptions = () =>
    mutationOptions({
        mutationFn: (timezone: string) =>
            unwrapEden(api.system.timezone.put({ timezone })),
    });

export const systemSshMutationOptions = () =>
    mutationOptions({
        mutationFn: (enabled: boolean) =>
            enabled
                ? unwrapEden(api.system.ssh.enable.post())
                : unwrapEden(api.system.ssh.disable.post()),
    });

export const systemGithubUpdateMutationOptions = () =>
    mutationOptions({
        mutationFn: (repository?: string) =>
            unwrapEden(api.system.update.github.post({ repository })),
    });

export const systemUrlUpdateMutationOptions = () =>
    mutationOptions({
        mutationFn: (url: string) =>
            unwrapEden(api.system.update.url.post({ url })),
    });

export const systemUploadUpdateMutationOptions = () =>
    mutationOptions({
        mutationFn: async (file: File) => {
            const upload = await unwrapEden(
                api.system.update.upload.post({ size: file.size }),
            );
            if (!upload) throw new Error("Could not start update upload");
            const uploadId = upload.uploadId;
            const uploadChunk = async (start: number, end: number) => {
                const response = await fetch(
                    `${apiUrl}/system/update/upload/${encodeURIComponent(uploadId)}`,
                    {
                        method: "PUT",
                        credentials: "include",
                        headers: {
                            "content-type": "application/octet-stream",
                            "upload-offset": String(start),
                        },
                        body: file.slice(start, end),
                    },
                );
                if (response.ok) return response;

                const payload: unknown = await response
                    .json()
                    .catch(() => undefined);
                const message =
                    payload && typeof payload === "object" && "error" in payload
                        ? getErrorMessage(payload.error)
                        : undefined;
                throw new Error(
                    message ?? `Update upload failed (HTTP ${response.status})`,
                );
            };

            try {
                for (
                    let start = 0;
                    start < file.size;
                    start += upload.chunkSize
                ) {
                    const end = Math.min(start + upload.chunkSize, file.size);
                    await uploadChunk(start, end);
                }
            } catch (error) {
                await unwrapEden(
                    api.system.update.upload({ uploadId }).delete(),
                ).catch(() => undefined);
                throw error;
            }
        },
    });

export const systemRollbackMutationOptions = () =>
    mutationOptions({
        mutationFn: () => unwrapEden(api.system.update.rollback.post()),
    });

export const systemFactoryResetMutationOptions = () =>
    mutationOptions({
        mutationFn: () => unwrapEden(api.system["factory-reset"].post()),
    });

export const systemPowerMutationOptions = () =>
    mutationOptions({
        mutationFn: async (action: "restart" | "poweroff") =>
            action === "restart"
                ? await unwrapEden(api.system.power.reboot.post())
                : await unwrapEden(api.system.power.poweroff.post()),
    });

export const systemIpSettingsMutationOptions = () =>
    mutationOptions({
        mutationFn: ({ uuid, ...settings }: SystemIpSettingsInput) =>
            unwrapEden(
                api.system.connections["network-manager"]({ uuid }).ip.put(
                    settings,
                ),
            ),
    });

type RemoteAccessInput =
    | {
          provider: "cloudflare";
          action: "provision";
          token: string;
          origin?: string;
      }
    | {
          provider: "tailscale";
          action: "provision";
          authKey: string;
          hostname?: string;
          origin?: string;
      }
    | { provider: "cloudflare" | "tailscale"; action: "remove" };

export const systemRemoteAccessMutationOptions = () =>
    mutationOptions({
        mutationFn: (input: RemoteAccessInput) => {
            const remote = api.system.connections["remote-access"];
            if (input.action === "remove") {
                return input.provider === "cloudflare"
                    ? unwrapEden(remote["cloudflare-tunnel"].delete())
                    : unwrapEden(remote.tailscale.delete());
            }
            return input.provider === "cloudflare"
                ? unwrapEden(
                      remote["cloudflare-tunnel"].put({
                          token: input.token,
                          origin: input.origin || undefined,
                      }),
                  )
                : unwrapEden(
                      remote.tailscale.put({
                          authKey: input.authKey,
                          hostname: input.hostname || undefined,
                          origin: input.origin || undefined,
                      }),
                  );
        },
    });

export const systemAuthOriginMutationOptions = () =>
    mutationOptions({
        mutationFn: ({
            origin,
            action,
        }: {
            origin: string;
            action: "add" | "remove";
        }) =>
            action === "add"
                ? unwrapEden(
                      api.system.connections["auth-origins"].post({ origin }),
                  )
                : unwrapEden(
                      api.system.connections["auth-origins"].delete({ origin }),
                  ),
    });
