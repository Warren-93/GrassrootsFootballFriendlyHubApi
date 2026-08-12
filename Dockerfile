# Build stage - uses a full Maven image directly rather than ./mvnw, since
# this repo's Maven wrapper jar isn't checked in (see .gitignore/.mvn).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Dependencies first, so they're cached across builds that only change source.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B -DskipTests package

# Runtime stage - a JRE is enough, no JDK/Maven needed to just run the jar.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S gffh && adduser -S gffh -G gffh
COPY --from=build /app/target/*.jar app.jar
USER gffh

# Render (and most PaaS hosts) inject PORT at runtime; the app already reads
# it via ${PORT:8090} in application.yml, so no extra wiring is needed here.
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
