FROM node:22-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npm run build

FROM golang:1.26.6-alpine AS caddy-build
ARG CADDY_VERSION=v2.11.4
ENV GOPROXY=https://goproxy.cn,direct
WORKDIR /src
RUN go mod init local/caddy-build \
    && go get github.com/caddyserver/caddy/v2/cmd/caddy@"${CADDY_VERSION}" \
    && go get golang.org/x/net@v0.56.0 golang.org/x/text@v0.39.0 google.golang.org/grpc@v1.82.1 \
    && CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/caddy github.com/caddyserver/caddy/v2/cmd/caddy
RUN CGO_ENABLED=0 go build -trimpath \
    -ldflags="-s -w -X github.com/caddyserver/caddy/v2.CustomVersion=${CADDY_VERSION}" \
    -o /out/caddy github.com/caddyserver/caddy/v2/cmd/caddy

FROM alpine:3.24
ENV XDG_CONFIG_HOME=/config XDG_DATA_HOME=/data
RUN apk upgrade --no-cache \
    && apk add --no-cache ca-certificates mailcap tzdata \
    && addgroup -S -g 1000 caddy \
    && adduser -S -D -H -u 1000 -G caddy caddy \
    && mkdir -p /config /data /srv /etc/caddy \
    && chown -R 1000:1000 /config /data /srv /etc/caddy
COPY --from=caddy-build /out/caddy /usr/bin/caddy
COPY --from=build /workspace/dist /srv
COPY Caddyfile /etc/caddy/Caddyfile
RUN chown -R 1000:1000 /srv /data /config /etc/caddy
USER 1000:1000
EXPOSE 8080 8443
ENTRYPOINT ["caddy"]
CMD ["run", "--config", "/etc/caddy/Caddyfile", "--adapter", "caddyfile"]
