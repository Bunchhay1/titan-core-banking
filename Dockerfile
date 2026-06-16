FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY gradle ./gradle
COPY gradlew build.gradle settings.gradle gradle.properties ./
RUN chmod +x gradlew && sed -i '/org.gradle.java.home/d' gradle.properties && ./gradlew dependencies --no-daemon || true
COPY src ./src
RUN ./gradlew clean build -x test --no-daemon

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
