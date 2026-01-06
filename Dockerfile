# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests clean package

# Run stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Install fontconfig for ImageIO text rendering support
RUN apt-get update && apt-get install -y fontconfig fonts-dejavu-core && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar app.jar

# Cloud Run listens on $PORT (we set server.port to use it)
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-Djava.awt.headless=true", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
