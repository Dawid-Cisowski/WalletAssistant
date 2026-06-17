# --- build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew ./
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./

RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon || true

COPY src src

RUN ./gradlew bootJar --no-daemon -x test

# --- CDS training stage ---
FROM eclipse-temurin:21-jre AS cds
WORKDIR /app
COPY --from=build /app/build/libs/*.jar /app/app.jar

RUN java -Dspring.context.exit=onRefresh \
    -XX:ArchiveClassesAtExit=/app/app.jsa \
    -Dspring.flyway.enabled=false \
    -Dspring.jpa.hibernate.ddl-auto=none \
    -Dspring.datasource.url=jdbc:postgresql://localhost:5432/cds \
    -Dspring.datasource.hikari.initialization-fail-timeout=-1 \
    -jar /app/app.jar || true

# --- runtime stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/build/libs/*.jar /app/app.jar
COPY --from=cds /app/app.jsa /app/app.jsa

ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=production

RUN useradd --no-create-home --shell /bin/false appuser
USER appuser
EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:SharedArchiveFile=/app/app.jsa", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dserver.port=${PORT}", \
  "-jar", "/app/app.jar"]
