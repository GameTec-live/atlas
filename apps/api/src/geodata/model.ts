import type { Static } from "@sinclair/typebox";
import { t } from "elysia";

const artifactKind = t.Union([
    t.Literal("pbf"),
    t.Literal("geocoder"),
    t.Literal("map"),
]);

const bounds = t.Object({
    minLongitude: t.Number({ minimum: -180, maximum: 180 }),
    minLatitude: t.Number({ minimum: -90, maximum: 90 }),
    maxLongitude: t.Number({ minimum: -180, maximum: 180 }),
    maxLatitude: t.Number({ minimum: -90, maximum: 90 }),
});

const errorResponse = t.Object({
    error: t.Object({
        code: t.String(),
        message: t.String(),
    }),
});

const diskSpace = t.Object({
    free_bytes: t.Integer({ minimum: 0 }),
    total_bytes: t.Integer({ minimum: 0 }),
});

const estimatedDatasetSize = t.Object({
    pbf: t.Integer({ minimum: 0 }),
    geocoder_estimate: t.Integer({ minimum: 0 }),
    map_estimate: t.Integer({ minimum: 0 }),
    total_estimate: t.Integer({ minimum: 0 }),
});

const catalogItem = t.Object({
    id: t.String(),
    name: t.String(),
    parent: t.Optional(t.String()),
    bounds: t.Optional(bounds),
    size_bytes: t.Optional(estimatedDatasetSize),
});

const upstreamRegion = t.Object({
    ...catalogItem.properties,
    country_codes: t.Optional(t.Array(t.String())),
    pbf_url: t.String(),
});

const upstreamArtifact = t.Object({
    kind: artifactKind,
    path: t.String(),
    size_bytes: t.Integer({ minimum: 0 }),
    modified_at: t.String(),
});

const dataset = t.Object({
    id: t.String(),
    name: t.String(),
    state: t.Union([t.Literal("ready"), t.Literal("degraded")]),
    source_type: t.Union([
        t.Literal("catalog"),
        t.Literal("url"),
        t.Literal("bbox"),
    ]),
    bounds: t.Optional(bounds),
    excludeRoads: t.Boolean(),
    last_checked_at: t.Optional(t.String()),
    updated_at: t.Optional(t.String()),
    size_bytes: t.Object({
        pbf: t.Optional(t.Integer({ minimum: 0 })),
        geocoder: t.Optional(t.Integer({ minimum: 0 })),
        map: t.Optional(t.Integer({ minimum: 0 })),
        total: t.Integer({ minimum: 0 }),
    }),
    installed_at: t.String(),
});

const upstreamDataset = t.Object({
    id: t.String(),
    name: t.String(),
    state: dataset.properties.state,
    source_type: dataset.properties.source_type,
    source_region: t.Optional(t.String()),
    source_url: t.String(),
    country_code: t.Optional(t.String()),
    bounds: t.Optional(bounds),
    exclude_roads: t.Boolean(),
    source_etag: t.Optional(t.String()),
    source_last_modified: t.Optional(t.String()),
    last_checked_at: t.Optional(t.String()),
    updated_at: t.Optional(t.String()),
    artifacts: t.Array(upstreamArtifact),
    installed_at: t.String(),
});

const jobState = t.Union([
    t.Literal("queued"),
    t.Literal("running"),
    t.Literal("completed"),
    t.Literal("failed"),
    t.Literal("cancelled"),
]);

const job = t.Object({
    id: t.String(),
    operation: t.Union([
        t.Literal("install"),
        t.Literal("update"),
        t.Literal("delete"),
    ]),
    dataset_id: t.String(),
    state: jobState,
    stage: t.String(),
    progress: t.Number({ minimum: 0, maximum: 1 }),
    error: t.Optional(t.String()),
    created_at: t.String(),
    started_at: t.Optional(t.String()),
    finished_at: t.Optional(t.String()),
});

const upstreamJobRequest = t.Object({
    name: t.Optional(t.String()),
    dataset_id: t.Optional(t.String()),
    source_type: t.Optional(t.String()),
    bbox: t.Optional(bounds),
    excludeRoads: t.Boolean(),
    region: t.Optional(upstreamRegion),
});

const upstreamJob = t.Object({
    ...job.properties,
    bytes_done: t.Optional(t.Integer({ minimum: 0 })),
    bytes_total: t.Optional(t.Integer({ minimum: 0 })),
    request: upstreamJobRequest,
});

const jobResponse = t.Union([job, errorResponse]);
const upstreamJobResponse = t.Union([upstreamJob, errorResponse]);

const jobUpdate = t.Union([
    t.Object({
        type: t.Literal("snapshot"),
        jobs: t.Array(job),
    }),
    t.Object({
        type: t.Literal("job"),
        job,
    }),
]);

const upstreamJobUpdate = t.Union([
    t.Object({
        type: t.Literal("snapshot"),
        jobs: t.Array(upstreamJob),
    }),
    t.Object({
        type: t.Literal("job"),
        job: upstreamJob,
    }),
]);

const catalogInstall = t.Object({
    id: t.String({ minLength: 1 }),
    excludeRoads: t.Optional(t.Boolean()),
});

const urlInstall = t.Object({
    url: t.String({ minLength: 1 }),
    excludeRoads: t.Optional(t.Boolean()),
});

const boundedInstall = t.Object({
    id: t.Optional(t.String({ minLength: 1 })),
    excludeRoads: t.Optional(t.Boolean()),
    minLongitude: bounds.properties.minLongitude,
    minLatitude: bounds.properties.minLatitude,
    maxLongitude: bounds.properties.maxLongitude,
    maxLatitude: bounds.properties.maxLatitude,
});

export const GeodataModel = {
    catalogQuery: t.Object({
        q: t.Optional(t.String()),
        parent: t.Optional(t.String()),
    }),
    catalogResponse: t.Union([
        t.Object({
            items: t.Array(catalogItem),
            count: t.Integer({ minimum: 0 }),
            disk_space: t.Optional(diskSpace),
        }),
        errorResponse,
    ]),
    datasetsResponse: t.Object({
        items: t.Array(dataset),
        count: t.Integer({ minimum: 0 }),
        disk_space: t.Optional(diskSpace),
    }),
    installBody: t.Union([catalogInstall, urlInstall, boundedInstall]),
    jobsQuery: t.Object({
        active: t.Optional(t.Boolean()),
    }),
    jobsResponse: t.Object({
        items: t.Array(job),
        count: t.Integer({ minimum: 0 }),
    }),
    jobResponse,
    jobUpdate,
    upstream: {
        catalogResponse: t.Union([
            t.Object({
                items: t.Array(upstreamRegion),
                count: t.Integer({ minimum: 0 }),
                disk_space: t.Optional(diskSpace),
            }),
            errorResponse,
        ]),
        datasetsResponse: t.Object({
            items: t.Array(upstreamDataset),
            count: t.Integer({ minimum: 0 }),
            disk_space: t.Optional(diskSpace),
        }),
        jobsResponse: t.Object({
            items: t.Array(upstreamJob),
            count: t.Integer({ minimum: 0 }),
        }),
        jobResponse: upstreamJobResponse,
        jobUpdate: upstreamJobUpdate,
    },
} as const;

export type InstallBody = Static<typeof GeodataModel.installBody>;
export type UpstreamCatalogResponse = Static<
    typeof GeodataModel.upstream.catalogResponse
>;
export type UpstreamDataset = Static<typeof upstreamDataset>;
export type UpstreamJob = Static<typeof upstreamJob>;
export type UpstreamJobResponse = Static<typeof upstreamJobResponse>;
export type UpstreamJobUpdate = Static<typeof upstreamJobUpdate>;
