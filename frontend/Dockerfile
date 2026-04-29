# ── Build Stage ──────────────────────────────────────────────
FROM node:20-alpine AS builder
WORKDIR /build
ARG NPM_REGISTRY=
RUN if [ -n "$NPM_REGISTRY" ]; then npm config set registry "$NPM_REGISTRY"; fi
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# ── Runtime Stage（Nginx）─────────────────────────────────────
FROM nginx:alpine
COPY --from=builder /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
