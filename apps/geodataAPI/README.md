# geodataAPI

A small Gin service that downloads and manages OpenStreetMap data for Atlas. It keeps one routing PBF and one geocoder SQLite pack per installed dataset, plus one combined `map.pmtiles` for the map server.

## What it does

- Lists the current [Geofabrik](https://download.geofabrik.de/) download catalog, with name and parent filters.
- Installs a named Geofabrik extract or a custom bounding-box extract.
- Generates [geocoder-go](https://github.com/GameTec-live/geocoder-go) SQLite packs with `packgen`.
- Merges installed PBFs with [osmium](https://osmcode.org/osmium-tool/manual.html) and builds the single PMTiles archive with [Planetiler](https://github.com/onthegomap/planetiler).
- Persists installed datasets and job history across restarts.
- Reports job progress over REST and WebSocket.
- Deletes dataset-owned files and removes/rebuilds the shared map archive.

Jobs are processed serially. This deliberately prevents two expensive imports from writing `map.pmtiles` at the same time. All final artifacts are moved into place only after their build succeeds.

## Requirements

- Go 1.26+
- `osmium` on `PATH` for bounding-box extracts and merging multiple PBFs
- the `packgen` binary from geocoder-go for SQLite output
- Java and a Planetiler JAR for PMTiles output

The service can manage PBF-only downloads without `packgen`, Java, or Planetiler. Send `"products":["pbf"]` in that case. If `products` is omitted, all three products are requested and the job fails clearly if a required processor is unavailable.

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

All errors use `{"error":{"code":"...","message":"..."}}`. Download and delete calls return `202 Accepted` and a job. Follow the `Location` response header or subscribe to the WebSocket.

### Options and state

```http
GET /healthz
GET /api/v1/options?q=austria&parent=europe
GET /api/v1/options/products
GET /api/v1/installed
```

`/options/products` shows whether `osmium`, `packgen`, Java, and the configured Planetiler JAR are currently available. Installed datasets have a `ready` state when every managed artifact exists and `degraded` when a file has disappeared outside this service.

### Start by name

The name can be a Geofabrik ID or its display name.

```sh
curl -X POST http://localhost:8080/api/v1/downloads/name \
  -H "Content-Type: application/json" \
  -d '{"name":"austria","products":["pbf","geocoder","map"]}'
```

### Start by bounding box

Coordinates are WGS84 longitude/latitude. The service chooses the smallest Geofabrik extract whose published envelope covers the box, downloads it, and invokes `osmium extract`. The optional `id` becomes the stable dataset/file name.

```sh
curl -X POST http://localhost:8080/api/v1/downloads/bbox \
  -H "Content-Type: application/json" \
  -d '{
    "id":"vienna",
    "bbox":{"west":16.17,"south":48.10,"east":16.58,"north":48.33},
    "products":["pbf","geocoder","map"]
  }'
```

### Jobs and live progress

```http
GET    /api/v1/downloads
GET    /api/v1/downloads?active=true
GET    /api/v1/downloads/{job-id}
DELETE /api/v1/downloads/{job-id}
WS     /api/v1/downloads/ws
```

The WebSocket first emits `{"type":"snapshot","jobs":[...]}`, followed by `{"type":"job","job":{...}}` updates. `progress` ranges from `0` to `1`; exact byte counters are included while downloading when the upstream server supplies a content length.

### Delete installed data

```http
DELETE /api/v1/data/{dataset-id}
```

Deletion is also a job. It removes the dataset PBF and SQLite pack. Because `map.pmtiles` is shared, it is removed immediately and rebuilt from remaining PBFs when Planetiler is configured. This guarantees that deleted geography is never left in the served map archive.

## Operational notes

- Planetiler's OpenMapTiles profile downloads supplemental Natural Earth and water data and can require 5–10 times the input PBF size as free working space.
- `osmium merge` expects normally sorted extracts. Geofabrik snapshots are suitable, but avoid combining snapshots from substantially different dates when they overlap.
- The manifest is `data/.geodata/state.json`. Do not edit it while the service is running.
- On restart, jobs that were queued or running are retained in history as failed/interrupted; partially built files live only under `.geodata/tmp` or use a `.part` suffix.
- OpenStreetMap-derived output remains subject to ODbL attribution requirements.

## Development

```sh
go test ./...
go vet ./...
go build ./...
```
