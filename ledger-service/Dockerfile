# --- build stage -------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# pom.xml is copied and dependencies resolved BEFORE the source is copied. Docker
# caches each layer, so as long as pom.xml is unchanged this layer is reused and a
# code-only change rebuilds without re-downloading the dependency tree. Copying
# everything at once would invalidate the cache on every edit.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# --- runtime stage -----------------------------------------------------------
# A JRE, not a JDK: nothing is compiled at runtime, so the compiler and the rest of
# the JDK toolchain are dead weight and extra attack surface. Debian-based rather
# than alpine because the healthcheck needs wget, which alpine's JRE image omits.
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# A non-root user, so a compromised process does not hold root inside the container.
RUN groupadd -r finpulse && useradd -r -g finpulse finpulse

COPY --from=build /build/target/ledger-service-0.1.0.jar app.jar
USER finpulse
EXPOSE 8080

# Compose gates ledger-service startup on this, so it reports healthy only once the
# application is genuinely serving, not merely once the process exists.
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
