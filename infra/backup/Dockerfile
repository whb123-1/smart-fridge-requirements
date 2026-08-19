FROM golang:1.26.6-alpine AS restic
ARG RESTIC_VERSION=v0.19.1
ENV GOPROXY=https://goproxy.cn,direct
WORKDIR /src
RUN go mod init local/restic-build \
    && go get github.com/restic/restic/cmd/restic@"${RESTIC_VERSION}" \
    && go get golang.org/x/net@v0.56.0 golang.org/x/text@v0.39.0 google.golang.org/grpc@v1.82.1 \
    && CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/restic github.com/restic/restic/cmd/restic

FROM mysql:8.4.11
COPY --from=restic /out/restic /usr/bin/restic
COPY entrypoint.sh backup.sh restore-drill.sh /opt/xianzhi/
RUN microdnf update -y \
    && microdnf clean all \
    && rm -rf /var/cache/dnf /usr/local/bin/gosu /usr/bin/mysqlsh /usr/lib/mysqlsh \
    && chmod 0555 /opt/xianzhi/*.sh \
    && mkdir -p /backups /metrics \
    && chown -R mysql:mysql /backups /metrics
USER mysql
ENTRYPOINT ["/opt/xianzhi/entrypoint.sh"]
