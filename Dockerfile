# REST server only (datingapp.app.api.RestApiServer). The CLI and JavaFX desktop app are not run from this image.

# ---- build ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# Dependencies first, so source edits do not re-download them.
COPY pom.xml ./
COPY .mvn .mvn
COPY config/quality config/quality
RUN mvn -B -q -Dcheckstyle.skip=true dependency:go-offline -DincludeScope=runtime || true

COPY src/main src/main
# Tests and the quality gate run before an image is built (scripts/run_verify.ps1), not here.
# JavaFX is left out of the runtime libraries: the REST server never loads it.
RUN mvn -B -q -Dcheckstyle.skip=true -DskipTests compile dependency:copy-dependencies \
        -DincludeScope=runtime -DexcludeGroupIds=org.openjfx -DoutputDirectory=target/lib

# ---- runtime ----
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd --system --uid 10001 --home-dir /app datingapp \
    && mkdir -p /app/data/photos /app/logs \
    && chown -R datingapp /app/data /app/logs

COPY --from=build /build/target/classes classes
COPY --from=build /build/target/lib lib
# The config path is resolved relative to the working directory.
COPY config/app-config.json config/app-config.json

USER datingapp

# Sized for a 512 MB container: heap is capped well below it to leave room for
# metaspace, thread stacks and direct buffers.
ENV JAVA_OPTS="-Xmx256m -Xss512k -XX:+UseSerialGC -XX:MaxDirectMemorySize=64m" \
    PORT=10000 \
    DATING_APP_LOG_LEVEL=INFO
# logback.xml falls back to DEBUG when DATING_APP_LOG_LEVEL is unset; DEBUG logs request internals.
EXPOSE 10000

# Shell form so $PORT (set by the host) is expanded; exec so SIGTERM reaches the JVM shutdown hook.
# A non-loopback bind needs DATING_APP_REST_SHARED_SECRET, or the server refuses to start.
CMD ["sh", "-c", "exec java $JAVA_OPTS --enable-preview --enable-native-access=ALL-UNNAMED -Djava.awt.headless=true -cp 'classes:lib/*' datingapp.app.api.RestApiServer --host=0.0.0.0 --port=${PORT}"]
