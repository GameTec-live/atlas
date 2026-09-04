import { SystemModel } from "./model";

const unavailable = { 503: SystemModel.error } as const;
const readErrors = {
    500: SystemModel.error,
    ...unavailable,
} as const;
const mutationErrors = {
    400: SystemModel.error,
    409: SystemModel.error,
    ...unavailable,
} as const;
const remoteAccessErrors = {
    ...mutationErrors,
    500: SystemModel.error,
} as const;

export const SystemResponse = {
    availability: SystemModel.availability,
    containers: {
        200: SystemModel.containers,
        ...readErrors,
    },
    updateStatus: {
        200: SystemModel.updateStatus,
        ...readErrors,
    },
    uploadStart: {
        201: SystemModel.uploadStart,
        400: SystemModel.error,
        409: SystemModel.error,
        413: SystemModel.error,
        422: SystemModel.error,
        500: SystemModel.error,
        503: SystemModel.error,
    },
    uploadChunk: {
        200: SystemModel.uploadProgress,
        202: SystemModel.updateAccepted,
        400: SystemModel.error,
        404: SystemModel.error,
        409: SystemModel.error,
        413: SystemModel.error,
        422: SystemModel.error,
        500: SystemModel.error,
        503: SystemModel.error,
    },
    uploadCancel: {
        200: SystemModel.ok,
        404: SystemModel.error,
        409: SystemModel.error,
        500: SystemModel.error,
    },
    updateDownload: {
        202: SystemModel.updateAccepted,
        400: SystemModel.error,
        409: SystemModel.error,
        413: SystemModel.error,
        422: SystemModel.error,
        500: SystemModel.error,
        502: SystemModel.error,
        503: SystemModel.error,
    },
    mutation: {
        200: SystemModel.ok,
        ...mutationErrors,
    },
    reboot: {
        202: SystemModel.rebootAccepted,
        409: SystemModel.error,
        ...unavailable,
    },
    poweroff: {
        202: SystemModel.poweroffAccepted,
        409: SystemModel.error,
        ...unavailable,
    },
    factoryReset: {
        202: SystemModel.factoryResetAccepted,
        409: SystemModel.error,
        500: SystemModel.error,
        ...unavailable,
    },
    ssh: {
        200: SystemModel.ssh,
        ...readErrors,
    },
    timezone: {
        200: SystemModel.timezone,
        ...readErrors,
    },
    timezoneUpdate: {
        200: SystemModel.timezone,
        ...mutationErrors,
    },
    adapters: {
        200: SystemModel.adapters,
        ...unavailable,
    },
    connections: {
        200: SystemModel.connections,
        ...readErrors,
    },
    devices: {
        200: SystemModel.devices,
        ...readErrors,
    },
    accessPoints: {
        200: SystemModel.accessPoints,
        ...readErrors,
    },
    ipSettings: {
        200: SystemModel.ipSettingsResponse,
        400: SystemModel.error,
        ...unavailable,
    },
    origins: {
        200: SystemModel.origins,
        ...readErrors,
    },
    originsMutation: {
        200: SystemModel.origins,
        ...mutationErrors,
    },
    remoteAccess: {
        200: SystemModel.remoteAccess,
        ...readErrors,
    },
    remoteAccessMutation: {
        200: SystemModel.remoteAccess,
        ...remoteAccessErrors,
    },
} as const;
