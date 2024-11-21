FROM openjdk:22-ea-31-jdk-slim-bullseye

COPY /build/libs/bluesky-reposter-all.jar /bin/runner/run.jar
WORKDIR /bin/runner

CMD ["java","-jar","run.jar"]