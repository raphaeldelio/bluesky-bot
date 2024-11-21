# Bluesky Bot

A Kotlin-based bot for interacting with the Bluesky platform. This bot fetches posts based on specified tags and performs actions such as reposting and following users. The bot leverages Redis for caching and scheduling tasks at specified intervals.

## Features

- Fetches posts from Bluesky based on predefined tags.
- Performs actions like reposting and following users.
- Utilizes Redis for state management and caching.
- Periodically runs tasks using a customizable scheduler.

## Requirements

- JDK 22 or higher
- Redis server

## Configuration

The bot reads configuration from a config.yaml file. Here’s an example:
```yaml
redis:
  host: localhost
  port: 6379
  username: user
  password: pass

bluesky:
  api-url: https://bsky.social/xrpc
  username: your-bluesky-username
  password: your-bluesky-password (Use an API password)

poster:
  since: 2024-01-01T00:00:00Z
  scheduler:
    frequencyminutes: 15
  tags:
    - java
    - javabubble
    - SpringBoot
    - Kotlin
    - anyothertag
```

since: ISO-8601 timestamp indicating the starting point if it has never run before

## Running Locally

### Build the Application:

`./gradlew build`

### Run the Application:

`java -jar build/libs/bluesky-reposter-all.jar`

Ensure config.yaml is in the same directory as the JAR file under the config folder:
`/config/config.yaml`

## Running with Docker

### Make sure you've built the application using the steps above.

### Build the Docker Image:
```
docker build -t bluesky-bot .
```

### Run the Container:
```
docker run --rm \
-v /path/to/config.yaml:/config/config.yaml \
bluesky-bot
```

## Running from Docker Hub

You can run the Bluesky Bot directly from the prebuilt Docker image available at raphaeldelio/bluesky-bot.

### Pull the Docker Image

Download the image from Docker Hub:
`docker pull raphaeldelio/bluesky-bot:latest`

### Prepare Your Configuration File

Ensure you have a `config.yaml` file ready with your settings. Place it in a directory on your host machine (e.g., /path/to/config.yaml).

### Run the Container

Use the following command to run the bot:
```
docker run --rm \
-v /path/to/config.yaml:/config/config.yaml \
raphaeldelio/bluesky-bot:latest
```

## Contributing

Feel free to submit issues or pull requests to improve this bot.

## License

This project is licensed under the MIT License