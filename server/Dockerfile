FROM eclipse-temurin:27-jre-alpine@sha256:0faf24c1d1e778bbc0700fc71c53b081c9432f1e51e6e41c86c63840e8b89ac1

RUN addgroup -S -g 10001 know \
    && adduser -S -D -H -u 10001 -G know know \
    && mkdir -p /app/config \
    && ln -s /etc/secrets/secrets.yml /app/config/secrets.yml
WORKDIR /app
COPY --chmod=0444 build/libs/know-server.jar /app/know-server.jar

ENV PORT=10000
EXPOSE 10000
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/know-server.jar"]
