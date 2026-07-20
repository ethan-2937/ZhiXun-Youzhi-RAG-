# syntax=docker/dockerfile:1

FROM node:22-alpine AS build
WORKDIR /workspace

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

COPY index.html vite.config.js ./
COPY src ./src
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=build --chown=nginx:nginx /workspace/dist /usr/share/nginx/html

USER nginx
EXPOSE 8080

ENTRYPOINT []
CMD ["nginx", "-g", "daemon off;"]
