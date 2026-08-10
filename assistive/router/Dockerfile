# Build from the monorepo root:
# docker build --file assistive/router/Dockerfile --tag atlas-router .
FROM ghcr.io/valhalla/valhalla-scripted:latest

ENTRYPOINT ["/bin/bash", "-c", "find /custom_files -maxdepth 1 -type l -name '*.pbf' -delete && if [[ -d /pbf ]]; then find /pbf -type f -name '*.pbf' -exec ln -sf -- {} /custom_files/ \\;; fi && exec /valhalla/scripts/docker-entrypoint.sh \"$@\"", "--"]
CMD ["build_tiles"]

HEALTHCHECK --interval=30s --timeout=30s --start-period=5s --retries=3 CMD curl -f http://localhost:8002/status || exit 1
