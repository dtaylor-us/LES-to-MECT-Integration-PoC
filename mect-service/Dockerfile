# Build
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B || true

COPY src src
RUN mvn package -DskipTests -B

# Run
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget
WORKDIR /app

RUN adduser -D -u 1000 appuser
COPY --from=build /app/target/*.jar app.jar
USER appuser

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
