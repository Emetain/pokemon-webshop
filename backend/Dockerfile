FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon || true

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
RUN useradd --uid 1001 --create-home appuser
WORKDIR /app
COPY --from=build /app/build/libs/*.jar /app/app.jar
# A new volume takes over this owner, so the app can write its uploads there.
RUN mkdir /app/media && chown appuser /app/media
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
