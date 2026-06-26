FROM golang:1.24.2-alpine AS build

ARG GOPROXY=https://goproxy.cn,direct
ENV GOPROXY=${GOPROXY}

WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -o /out/wechat-observatory ./cmd/bridge
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -o /out/wechat-observatory-db ./cmd/bridge-db

FROM alpine:3.21

RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories \
	&& apk add --no-cache ca-certificates wget
RUN adduser -D -H -u 10001 appuser \
	&& mkdir -p /var/lib/wechat-observatory/media \
	&& chown -R appuser:appuser /var/lib/wechat-observatory
COPY --from=build /out/wechat-observatory /usr/local/bin/wechat-observatory
COPY --from=build /out/wechat-observatory-db /usr/local/bin/wechat-observatory-db

USER appuser
EXPOSE 8088
ENTRYPOINT ["/usr/local/bin/wechat-observatory"]
