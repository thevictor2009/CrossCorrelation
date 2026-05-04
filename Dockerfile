# Стадия 1: Сборка приложения
FROM maven:3.9.4-eclipse-temurin-17 AS builder
WORKDIR /app

# Копируем pom.xml и скачиваем зависимости (кэшируется для ускорения сборки)
COPY pom.xml .
RUN mvn dependency:go-offline

# Копируем исходный код и собираем JAR
COPY src ./src
RUN mvn clean package -DskipTests

# Стадия 2: Запуск приложения
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Копируем собранный JAR из стадии builder
COPY --from=builder /app/target/CrossCorrelationWithBot-0.0.1-SNAPSHOT-jar-with-dependencies.jar app.jar

# Render ожидает, что приложение слушает порт 10000
EXPOSE 10000

# Запускаем приложение
ENTRYPOINT ["java", "-jar", "app.jar"]