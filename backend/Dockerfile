# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests package && \
    jar="$(find target -maxdepth 1 -type f -name '*.jar' -print -quit)" && \
    test -n "$jar" && cp "$jar" /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S zhixun && adduser -S zhixun -G zhixun

WORKDIR /app
COPY --from=build --chown=zhixun:zhixun /workspace/app.jar /app/app.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"
USER zhixun
EXPOSE 8088

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
