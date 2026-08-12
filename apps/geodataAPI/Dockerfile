# syntax=docker/dockerfile:1.7

FROM --platform=$BUILDPLATFORM golang:1.26-trixie AS api-build

ARG TARGETOS
ARG TARGETARCH

WORKDIR /src
COPY go.mod go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod \
    go mod download

COPY main.go ./
COPY cmd ./cmd
COPY internal ./internal
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 GOOS=$TARGETOS GOARCH=$TARGETARCH \
    go build -buildvcs=false -trimpath \
      -tags netgo,osusergo \
      -ldflags="-s -w" \
      -o /out/geodata-api . && \
    mkdir -p /out/data/.geodata/tmp


FROM api-build AS reloader-build

ARG TARGETOS
ARG TARGETARCH

RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 GOOS=$TARGETOS GOARCH=$TARGETARCH \
    go build -buildvcs=false -trimpath \
      -tags netgo,osusergo \
      -ldflags="-s -w" \
      -o /out/geodata-reloader ./cmd/reloader


FROM scratch AS reloader

ARG VERSION=dev
ARG COMMIT=unknown
ARG BUILD_DATE=unknown

LABEL org.opencontainers.image.title="Atlas geodata reloader" \
      org.opencontainers.image.description="Minimal Docker-compatible consumer reload API" \
      org.opencontainers.image.source="https://github.com/GameTec-live/atlas" \
      org.opencontainers.image.version="$VERSION" \
      org.opencontainers.image.revision="$COMMIT" \
      org.opencontainers.image.created="$BUILD_DATE"

COPY --from=reloader-build /out/geodata-reloader /app/geodata-reloader

USER 65532:65532

ENV RELOADER_LISTEN=:8080 \
    RELOADER_CONTAINER_SOCKET=/var/run/docker.sock \
    RELOADER_OPERATION_TIMEOUT=2m

EXPOSE 8080
STOPSIGNAL SIGTERM
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD ["/app/geodata-reloader", "healthcheck"]

ENTRYPOINT ["/app/geodata-reloader"]


FROM --platform=$BUILDPLATFORM golang:1.26-trixie AS packgen-build

ARG TARGETOS
ARG TARGETARCH
# Pin the source rather than building a moving main branch.
ARG GEOCODER_GO_REF=900119e14b0a5316fdba54901ceb46e24ce1fef7

WORKDIR /src/geocoder-go
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    git init . && \
    git remote add origin https://github.com/GameTec-live/geocoder-go.git && \
    git fetch --depth=1 origin "${GEOCODER_GO_REF}" && \
    git checkout --detach FETCH_HEAD && \
    CGO_ENABLED=0 GOOS=$TARGETOS GOARCH=$TARGETARCH \
    go build -buildvcs=false -trimpath -ldflags="-s -w" \
      -o /out/packgen ./cmd/packgen


FROM --platform=$TARGETPLATFORM debian:trixie-slim AS osmium-runtime

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install --no-install-recommends -y osmium-tool && \
    rm -rf /var/lib/apt/lists/* && \
    mkdir -p /out/usr/bin && \
    cp /usr/bin/osmium /out/usr/bin/osmium && \
    ldd /usr/bin/osmium \
      | awk '{ if ($3 ~ /^\//) print $3; else if ($1 ~ /^\//) print $1 }' \
      | sort -u \
      | while read -r library; do \
          case "$library" in \
            /lib64/*) continue ;; \
            /lib/*) target="/usr${library}" ;; \
            *) target="$library" ;; \
          esac; \
          mkdir -p "/out$(dirname "$target")"; \
          cp -L "$library" "/out${target}"; \
        done


FROM --platform=$BUILDPLATFORM debian:trixie-slim AS planetiler-download

ARG PLANETILER_VERSION=0.10.2
ARG PLANETILER_SHA256=f310bd0413e2e4512b27f4046d418664e8e1d3bf31603c2a70e23de06c167e4d

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install --no-install-recommends -y ca-certificates curl && \
    rm -rf /var/lib/apt/lists/* && \
    mkdir -p /out/opt/planetiler && \
    curl --fail --location --retry 3 \
      --output /out/opt/planetiler/planetiler.jar \
      "https://github.com/onthegomap/planetiler/releases/download/v${PLANETILER_VERSION}/planetiler.jar" && \
    echo "${PLANETILER_SHA256}  /out/opt/planetiler/planetiler.jar" | sha256sum --check --strict


FROM gcr.io/distroless/java25-debian13:nonroot AS geodata-api

ARG VERSION=dev
ARG COMMIT=unknown
ARG BUILD_DATE=unknown

LABEL org.opencontainers.image.title="Atlas geodata API" \
      org.opencontainers.image.description="Non-root geodata manager with osmium, geocoder-go packgen, and Planetiler" \
      org.opencontainers.image.source="https://github.com/GameTec-live/atlas" \
      org.opencontainers.image.version="$VERSION" \
      org.opencontainers.image.revision="$COMMIT" \
      org.opencontainers.image.created="$BUILD_DATE"

COPY --from=osmium-runtime /out/ /
COPY --from=packgen-build /out/packgen /usr/local/bin/packgen
COPY --from=planetiler-download /out/opt/planetiler/planetiler.jar /opt/planetiler/planetiler.jar
COPY --from=api-build /out/geodata-api /app/geodata-api
COPY --from=api-build --chown=65532:65532 /out/data /data

USER 65532:65532
WORKDIR /data

ENV GIN_MODE=release \
    GEODATA_LISTEN=:8080 \
    GEODATA_DIR=/data \
    GEODATA_OSMIUM=/usr/bin/osmium \
    GEODATA_PACKGEN=/usr/local/bin/packgen \
    GEODATA_JAVA=/usr/bin/java \
    GEODATA_PLANETILER_JAR=/opt/planetiler/planetiler.jar \
    GEODATA_PLANETILER_ARGS="--download-dir=/data/.geodata/planetiler/sources --tmpdir=/data/.geodata/planetiler/tmp" \
    SQLITE_TMPDIR=/data/.geodata/tmp \
    TMPDIR=/data/.geodata/tmp \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Djava.io.tmpdir=/data/.geodata/tmp"

VOLUME ["/data"]
EXPOSE 8080
STOPSIGNAL SIGTERM
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD ["/app/geodata-api", "healthcheck"]

ENTRYPOINT ["/app/geodata-api"]
