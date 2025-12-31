# ===============================
# 🏗️ STAGE 1: BUILD (With Gradle)
# ===============================
FROM gradle:8.5-jdk21 AS build
WORKDIR /app

# Copy ឯកសារកំណត់រចនាសម្ព័ន្ធ Gradle ជាមុន (ដើម្បី Cache Dependencies)
COPY build.gradle settings.gradle ./
COPY src ./src

# Build យក JAR file (bootJar) និងរំលងការ Test
# យើងប្រើ --no-daemon ដើម្បីកុំឱ្យវាស៊ី RAM ពេកក្នុង Docker
RUN gradle bootJar -x test --no-daemon

# ===============================
# 🚀 STAGE 2: RUNTIME (Lightweight)
# ===============================
FROM openjdk:21-jdk-slim
WORKDIR /app

# Copy JAR ពី build stage
# ចំណាំ: Gradle បង្កើត JAR នៅ build/libs/
COPY --from=build /app/build/libs/*.jar app.jar

# Expose Port
EXPOSE 8080

# Run App
ENTRYPOINT ["java", "-jar", "app.jar"]