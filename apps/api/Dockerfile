# Build from the monorepo root:
# docker build --file apps/api/Dockerfile --tag atlas-api \
#   --build-arg BUILD_VERSION=<version> --build-arg BUILD_TIME=<timestamp> \
#   --build-arg GIT_COMMIT=<commit> .
FROM oven/bun:1.3.14 AS build

WORKDIR /workspace

# Install from manifests first so dependency layers remain cached when only application source changes.
COPY package.json bun.lock ./
COPY apps/api/package.json ./apps/api/package.json
COPY apps/web/package.json ./apps/web/package.json
COPY mobile/package.json ./mobile/package.json
COPY packages/biome/package.json ./packages/biome/package.json
COPY packages/typescript-config/package.json ./packages/typescript-config/package.json

RUN --mount=type=cache,target=/root/.bun/install/cache \
    bun install --frozen-lockfile

COPY packages ./packages
COPY apps/api ./apps/api

ENV NODE_ENV=production

ARG BUILD_VERSION=development
ARG BUILD_TIME=unknown
ARG GIT_COMMIT=unknown

RUN bun run --cwd apps/api build

FROM gcr.io/distroless/base:nonroot AS runtime

WORKDIR /app

COPY --from=build --chown=65532:65532 /workspace/apps/api/dist/server server
COPY --from=build --chown=65532:65532 /workspace/apps/api/drizzle drizzle

ENV NODE_ENV=production

EXPOSE 3000

USER 65532:65532

ENTRYPOINT ["/app/server"]
