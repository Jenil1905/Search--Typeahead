# Stage 1: Build stage
FROM maven:3.8.8-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml to cache dependency downloads
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build jar
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy jar from build stage
COPY --from=build /app/target/typeahead-0.0.1-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8080

# Run Spring Boot app
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
