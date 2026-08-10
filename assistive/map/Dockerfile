# Build from the monorepo root:
# docker build --file assistive/map/Dockerfile --tag atlas-map .
FROM ghcr.io/maplibre/martin:latest

COPY assistive/map/config /config
COPY assistive/map/data /data

CMD ["--config", "/config/config.yaml", "--webui", "enable-for-all"]