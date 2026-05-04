# Используем современный образ с Maven 3.9 и Java 17
FROM maven:3.9.4-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src

RUN echo "=== Содержимое /app ===" && ls -la /app
RUN echo "=== Содержимое /app/src ===" && ls -la /app/src || echo "src не скопировалась!"

RUN mvn clean package -DskipTests

# Финальный образ с Java 17
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY --from=builder /app/target/*-with-dependencies.jar app.jar
EXPOSE 8080
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "app.jar"]