# geodataAPI

A small Gin service that downloads and manages OpenStreetMap data for Atlas. It keeps one routing PBF and one geocoder SQLite pack per installed dataset, plus one combined `map.pmtiles` for the map server.

## What it does

- Lists the current [Geofabrik](https://download.geofabrik.de/) download catalog, with name and parent filters.
- Installs a catalog extract, a custom PBF URL, or a bounding-box dataset.
- Generates a [geocoder-go](https://github.com/GameTec-live/geocoder-go) SQLite pack for every dataset with `packgen`.
- Merges installed PBFs with [osmium](https://osmcode.org/osmium-tool/manual.html) and builds the single PMTiles archive with [Planetiler](https://github.com/onthegomap/planetiler).
- Persists installed datasets and job history across restarts.
- Reports job progress over REST and WebSocket.
- Deletes dataset-owned files and removes/rebuilds the shared map archive.
- Reloads the router and geocoder after successful changes when a Docker-compatible runtime socket is available.

Jobs are processed serially. This deliberately prevents two expensive imports from writing `map.pmtiles` at the same time. A dataset is one lifecycle unit: every successful install has its PBF and SQLite files and is included in the shared `map.pmtiles`.

## Requirements

- Go 1.26+
- `osmium` on `PATH` for bounding-box extracts and merging multiple PBFs
- the `packgen` binary from geocoder-go for SQLite output
- Java and a Planetiler JAR for PMTiles output

All processors are required. An install fails without registering the dataset if its PBF download, SQLite conversion, or PMTiles build fails.

## Run

```sh
go run .
```

The default listen address is `:8080`, and the default datastore is `./data`.

```text
data/
├── austria.osm.pbf
├── austria.sqlite
├── germany.osm.pbf
├── germany.sqlite
├── map.pmtiles
└── .geodata/
    ├── state.json
    ├── tmp/
    └── planetiler/  # internal source cache and build scratch space
```

## Container

The included multi-stage image contains every processor used by the API:

- the statically linked `geodata-api` service
- the statically linked geocoder-go `packgen` converter
- `osmium` and only its required shared libraries
- a Java 25 runtime and the checksum-verified Planetiler JAR

The final image uses Distroless Java 25 on Debian 13, has no shell or package manager, and runs as UID/GID `65532`. Planetiler is pinned to `0.10.2`, while geocoder-go is pinned to a commit; both can be overridden with build arguments. All Debian-based build and runtime stages use Debian 13/Trixie.

```sh
docker build -t atlas-geodata-api .
docker run --rm \
  --name atlas-geodata-api \
  --user 65532:65532 \
  --cap-drop ALL \
  --security-opt no-new-privileges \
  --read-only \
  --tmpfs /tmp:rw,noexec,nosuid,size=64m \
  -p 8080:8080 \
  -v "$(pwd)/data:/data" \
  atlas-geodata-api
```

The mounted host directory must be writable by UID/GID `65532`. The root filesystem can remain read-only because all downloads, build intermediates, persistent state, and final artifacts are written beneath `/data`. The image has a built-in `/healthz` healthcheck implemented by the API binary, so no `curl` or shell is included.

For large map builds, set an explicit memory limit. Java uses up to 75% of the container limit by default:

```sh
docker run --memory=8g -p 8080:8080 -v "$(pwd)/data:/data" atlas-geodata-api
```

Build metadata and tool pins can be supplied explicitly:

```sh
docker build \
  --build-arg VERSION=1.0.0 \
  --build-arg COMMIT="$(git rev-parse HEAD)" \
  --build-arg BUILD_DATE="$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --build-arg PLANETILER_VERSION=0.10.2 \
  --build-arg PLANETILER_SHA256=f310bd0413e2e4512b27f4046d418664e8e1d3bf31603c2a70e23de06c167e4d \
  --build-arg GEOCODER_GO_REF=a8a20bc147554ede99f06e8441d835c8b7373181 \
  -t atlas-geodata-api:1.0.0 .
```

## Configuration

| Environment variable | Default | Meaning |
| --- | --- | --- |
| `GEODATA_LISTEN` | `:8080` | HTTP listen address |
| `GEODATA_HEALTHCHECK_URL` | `http://127.0.0.1:8080/healthz` | Container healthcheck target; change it with the listen port |
| `GEODATA_DIR` | `data` | Final datastore directory |
| `GEODATA_CATALOG_URL` | Geofabrik `index-v1.json` | Region catalog URL |
| `GEODATA_CATALOG_TTL` | `24h` | In-memory catalog lifetime; seconds or Go duration |
| `GEODATA_HTTP_TIMEOUT` | `24h` | PBF download timeout |
| `GEODATA_CONTAINER_SOCKET` | `/var/run/docker.sock` | Optional Docker-compatible Unix socket; reload is disabled when it does not exist |
| `GEODATA_RELOAD_TIMEOUT` | `30s` | Total timeout for container discovery and restart |
| `GEODATA_OSMIUM` | `osmium` | osmium executable name/path |
| `GEODATA_PACKGEN` | `packgen` | geocoder-go pack generator name/path |
| `GEODATA_JAVA` | `java` | Java executable name/path |
| `GEODATA_PLANETILER_JAR` | empty | Required path to `planetiler.jar` for map output |
| `GEODATA_PLANETILER_ARGS` | empty | Extra space-separated Planetiler arguments, for example JVM/profile tuning supported after the JAR arguments |

Example PowerShell setup:

```powershell
$env:GEODATA_DIR = "C:\atlas-data"
$env:GEODATA_PACKGEN = "C:\tools\packgen.exe"
$env:GEODATA_PLANETILER_JAR = "C:\tools\planetiler.jar"
go run .
```

## API

All errors use `{"error":{"code":"...","message":"..."}}`. Install and delete calls return `202 Accepted` and a job. Follow the `Location` response header or subscribe to the WebSocket.

### Catalog and datasets

```http
GET /healthz
GET /api/v1/catalog?q=austria&parent=europe
GET /api/v1/datasets
```

Datasets have a `ready` state when their PBF, SQLite pack, and shared PMTiles archive exist, and `degraded` when a managed file has disappeared outside this service.

Catalog entries include `size_bytes.pbf`, obtained from the download server's `Content-Length`, plus `geocoder_estimate`, `map_estimate`, and `total_estimate`. The estimates are intentionally conservative heuristics (3x the PBF for the road-inclusive geocoder pack and 1.5x for PMTiles); generated sizes vary with the contents of each extract. PBF sizes are fetched concurrently and cached for the catalog TTL. If a source does not expose its size, `size_bytes` is omitted for that entry.

Both catalog and dataset list responses include `disk_space.free_bytes` and `disk_space.total_bytes` for the filesystem containing the `data` directory when the operating system exposes that information. Installed dataset sizes are measured from their actual artifacts and include a calculated `size_bytes.total` in the simplified API.

### Install a dataset

Use the same endpoint for catalog, custom URL, and bounding-box sources. Catalog installs use the ID returned by `GET /api/v1/catalog`.

```sh
curl -X POST http://localhost:8080/api/v1/datasets \
  -H "Content-Type: application/json" \
  -d '{"id":"austria","excludeRoads":true}'
```

A custom source accepts an absolute HTTP or HTTPS URL whose path ends in `.pbf`. Its dataset ID is derived from the filename: for example, `custom-latest.osm.pbf` becomes `custom`. The URL and source type are stored with the dataset, so the normal update endpoint can check and replace it later.

```sh
curl -X POST http://localhost:8080/api/v1/datasets \
  -H "Content-Type: application/json" \
  -d '{"url":"https://geo.example/custom-latest.osm.pbf","excludeRoads":false}'
```

`excludeRoads` defaults to `false`. Set it to `true` to pass `--include-roads=false` to geocoder-go's `packgen`, substantially reducing the SQLite pack size when named-road results are not needed. It affects only the geocoder pack; routing PBF and PMTiles generation remain unchanged. The selected value is stored with the dataset and returned by dataset listings.

Coordinates are WGS84 longitude/latitude. The service chooses the smallest Geofabrik extract whose published envelope covers the box, downloads it, and invokes `osmium extract`. The optional `id` becomes the stable dataset/file name.

```sh
curl -X POST http://localhost:8080/api/v1/datasets \
  -H "Content-Type: application/json" \
  -d '{
    "id":"vienna",
    "excludeRoads":true,
    "bbox":{"minLongitude":16.17,"minLatitude":48.10,"maxLongitude":16.58,"maxLatitude":48.33}
  }'
```

### Jobs and live progress

```http
GET    /api/v1/jobs
GET    /api/v1/jobs?active=true
GET    /api/v1/jobs/{job-id}
DELETE /api/v1/jobs/{job-id}
WS     /api/v1/jobs/ws
```

The WebSocket first emits `{"type":"snapshot","jobs":[...]}`, followed by `{"type":"job","job":{...}}` updates. `progress` ranges from `0` to `1`; exact byte counters are included while downloading when the upstream server supplies a content length.

### Update a dataset

```http
POST /api/v1/datasets/{dataset-id}/update
```

Update requests return `202 Accepted` with an asynchronous `update` job. The service sends the source's stored `ETag` and `Last-Modified` validators with a conditional request. If the source returns `304 Not Modified`, the job completes with stage `up_to_date` without rebuilding files or restarting consumers. If validators are unavailable, the source is downloaded and rebuilt.

Updates preserve the dataset's bounding box and `excludeRoads` setting. The new PBF, SQLite pack, and combined `map.pmtiles` are built under `.geodata/tmp`; the installed files are replaced only after all processing succeeds. A conversion or commit failure keeps the previous artifacts and dataset metadata.

### Delete a dataset

```http
DELETE /api/v1/datasets/{dataset-id}
```

Deletion is also a job. It first builds a replacement `map.pmtiles` from the remaining datasets, then removes the dataset PBF and SQLite pack and commits the replacement map. If the rebuild fails, the original dataset and map remain available.

## Operational notes

- Planetiler's OpenMapTiles profile downloads supplemental Natural Earth and water data and can require 5–10 times the input PBF size as free working space.
- `osmium merge` expects normally sorted extracts. Geofabrik snapshots are suitable, but avoid combining snapshots from substantially different dates when they overlap.
- The manifest is `data/.geodata/state.json`. Do not edit it while the service is running.
- On restart, jobs that were queued or running are retained in history as failed/interrupted; partially built files live only under `.geodata/tmp` or use a `.part` suffix.
- The manifest retains the newest 100 completed, failed, or cancelled jobs. Queued and running jobs are always retained in addition to that history limit.
- Successful installs and deletions mark the consumer services for reload. Once no queued or running jobs remain, the service uses the mounted Docker-compatible socket to restart containers labeled `live.gametec.atlas.geodata-consumer=router` or `live.gametec.atlas.geodata-consumer=geocoder`. A missing socket disables this behavior; discovery or restart errors are logged and do not change the completed job result.
- The example Compose file mounts `${CONTAINER_SOCKET_PATH:-/var/run/docker.sock}`. This works with Docker and Podman's Docker-compatible API; set `CONTAINER_SOCKET_PATH` to a different host socket when needed. Podman's required `label=disable` security option is included. Because the API remains non-root, set `CONTAINER_SOCKET_GID` to the socket's numeric group ID if its group is not `0` (for example, `stat -c '%g' /var/run/docker.sock`). Mounting a container-runtime socket grants powerful control over the host and should only be used for this internal service on a trusted host.
- OpenStreetMap-derived output remains subject to ODbL attribution requirements.

## Development

```sh
go test ./...
go vet ./...
go build ./...
```
