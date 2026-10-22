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
# Sized for Render's 512 MB Starter instance. The heap gets 60% of the
# container's memory (the JVM's own default is only a quarter); the rest is
# left for what lives outside the heap - class metadata, compiled code and
# thread stacks - so the container doesn't hit its limit and get restarted.
# The serial collector uses the least memory and suits one small CPU, smaller
# thread stacks and code cache trim the rest, and stopping the JIT at its
# first tier starts faster and uses less memory, which is ample at this load.
# On a bigger instance, set JAVA_OPTS in Render to override these.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=64m -XX:TieredStopAtLevel=1"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
