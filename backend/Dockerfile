# Stage 1: Build with Gradle
FROM gradle:8.5-jdk21 AS builder

WORKDIR /app

# Copy Kotlin DSL gradle files (*.kts not *.gradle)
COPY settings.gradle.kts ./
COPY gradle.properties ./
COPY app/build.gradle.kts ./app/
COPY gradlew ./
COPY gradle ./gradle

# Copy source code
COPY app/src ./app/src

# Build the JAR (skip tests)
RUN gradle :app:bootJar --no-daemon -x test

# Stage 2: Run the JAR
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/app/build/libs/*.jar myapp.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "myapp.jar"]