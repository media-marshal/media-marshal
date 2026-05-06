# ── Build Stage ──────────────────────────────────────────────
FROM node:20-alpine AS builder
WORKDIR /build/frontend
ARG NPM_REGISTRY=
RUN if [ -n "$NPM_REGISTRY" ]; then npm config set registry "$NPM_REGISTRY"; fi
COPY frontend/package*.json ./
RUN npm ci
COPY VERSION ../VERSION
COPY frontend/. .
RUN npm run build

# ── Runtime Stage（Nginx）─────────────────────────────────────
FROM nginx:alpine
COPY --from=builder /build/frontend/dist /usr/share/nginx/html
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
