# ── Stage 1: Build ─────────────────────────────────────────────────────────────
FROM node:20-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts

COPY . .

ARG API_URL
ARG SENTRY_DSN
RUN sed -i "s|__API_URL__|${API_URL}|g" src/environments/environment.production.ts && \
    sed -i "s|__SENTRY_DSN__|${SENTRY_DSN}|g" src/environments/environment.production.ts

RUN npm run build -- --configuration production

# ── Stage 2: Runtime ───────────────────────────────────────────────────────────
FROM nginx:alpine AS runtime

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /app/dist/pph-frontend/browser /usr/share/nginx/html

# SPA fallback: route all requests to index.html
RUN printf 'server {\n\
    listen 80;\n\
    root /usr/share/nginx/html;\n\
    index index.html;\n\
    location / {\n\
        try_files $uri $uri/ /index.html;\n\
    }\n\
}\n' > /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
