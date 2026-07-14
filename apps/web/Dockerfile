# Build from the monorepo root:
# docker build --file apps/web/Dockerfile --tag atlas-web .
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
COPY apps/web ./apps/web

ENV NODE_ENV=production

RUN bun run --cwd apps/web build

FROM nginxinc/nginx-unprivileged:1.31.2-alpine AS runtime

COPY --from=build --chown=101:101 /workspace/apps/web/dist /usr/share/nginx/html
COPY --chown=101:101 apps/web/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 8080

USER 101:101

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --quiet --output-document=/dev/null http://127.0.0.1:8080/ || exit 1
