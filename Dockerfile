# Step 1: Use Maven to build the JAR inside Docker
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Step 2: Use OpenJDK 21 to run the application
FROM openjdk:21-jdk-slim
WORKDIR /app
COPY --from=builder /app/target/payment-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
