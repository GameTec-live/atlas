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

FROM caddy:2.11.4-alpine AS runtime

ARG CADDY_UID=10001
ARG CADDY_GID=10001

RUN addgroup --system --gid "${CADDY_GID}" caddy \
    && adduser --system --disabled-password --no-create-home --uid "${CADDY_UID}" --ingroup caddy caddy \
    && mkdir -p /config /data /etc/caddy/pki /srv \
    && chown -R "${CADDY_UID}:${CADDY_GID}" /config /data /srv

# Keep the official binary's low-port capability explicit for the non-root user.
RUN setcap cap_net_bind_service=+ep /usr/bin/caddy

COPY --from=build --chown=${CADDY_UID}:${CADDY_GID} /workspace/apps/web/dist /srv
COPY --chown=${CADDY_UID}:${CADDY_GID} apps/web/simulator /srv/simulator
COPY --chown=root:root --chmod=0444 apps/web/Caddyfile /etc/caddy/Caddyfile
COPY --chown=${CADDY_UID}:${CADDY_GID} --chmod=0444 apps/web/pki/atlas-local-root.crt /etc/caddy/pki/root.crt
COPY --chown=${CADDY_UID}:${CADDY_GID} --chmod=0400 apps/web/pki/atlas-local-root.key /etc/caddy/pki/root.key

EXPOSE 80 443 443/udp

USER ${CADDY_UID}:${CADDY_GID}

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --quiet --output-document=/dev/null http://127.0.0.1:2019/health || exit 1
