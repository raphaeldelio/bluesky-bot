FROM openjdk:22-ea-31-jdk-slim-bullseye

WORKDIR /app

COPY /build/libs/bluesky-reposter-all.jar /app/run.jar
COPY /src/main/resources/config-prod.yaml /config/config.yaml

CMD ["java","-jar","run.jar"]