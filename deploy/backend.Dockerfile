# The backend image for the server. GitHub Actions runs the tests and builds the jar first, so this image only adds
# the jar that passed them (build context: the repository root). For Docker Compose on your own computer,
# backend/Dockerfile builds from source instead.
FROM eclipse-temurin:21-jre
LABEL org.opencontainers.image.source=https://github.com/Emetain/pokemon-webshop
LABEL org.opencontainers.image.description="Poketopia backend"

RUN useradd --uid 1001 --create-home appuser \
    # A new volume takes over this owner, so the app can write its uploads there.
    && mkdir -p /app/media && chown appuser /app/media
WORKDIR /app
COPY backend/build/app.jar /app/app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
