# syntax=docker/dockerfile:1
# Production image for Railway. Builds the JAR in-stage so deploy works without a pre-built target/.

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/target/fitvision-backend-*.jar app.jar

RUN apk add --no-cache chromium nss freetype harfbuzz ca-certificates ttf-freefont && \
    if [ ! -e /usr/bin/chromium-browser ]; then ln -s /usr/bin/chromium /usr/bin/chromium-browser; fi
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
ENV CHROMIUM_PATH=/usr/bin/chromium-browser
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1

EXPOSE 8080
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
