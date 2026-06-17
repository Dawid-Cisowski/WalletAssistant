# --- build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew ./
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY integration-tests/build.gradle.kts integration-tests/build.gradle.kts

RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon || true

COPY src src

RUN ./gradlew bootJar --no-daemon -x test

# --- runtime stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/build/libs/*.jar /app/app.jar

ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=production

RUN useradd --no-create-home --shell /bin/false appuser
USER appuser
EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dserver.port=${PORT}", \
  "-jar", "/app/app.jar"]
