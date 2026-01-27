# ========================
# 🏗️ STAGE 1: BUILDER (ប្រើ Ubuntu ដើម្បីឱ្យស្គាល់ Protoc)
# ========================
# ⚠️ សំខាន់ណាស់: ហាមប្រើ "-alpine" នៅត្រង់នេះ!
# យើងត្រូវប្រើ JDK ពេញ (Ubuntu based) ដើម្បីឱ្យវា Run 'protoc' បាន
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# 1. Copy ឯកសារចាំបាច់
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./

# 2. ផ្តល់សិទ្ធិ Execute ទៅ gradlew
RUN chmod +x ./gradlew

# 3. Copy Source Code
COPY src/ src/

# 4. ចាប់ផ្តើម Build (ឥឡូវនេះវានឹងស្គាល់ protoc ហើយ)
RUN ./gradlew clean build -x test

# ========================
# 🚀 STAGE 2: RUNNER (ប្រើ Alpine ដើម្បីឱ្យស្រាល)
# ========================
FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app

# Copy JAR ដែល Build បានពី Stage 1
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]