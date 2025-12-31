# 1. ប្រើ Eclipse Temurin (Java 21) ដែលជាស្តង់ដារថ្មី
FROM eclipse-temurin:21-jdk

# 2. បង្កើត Folder នៅក្នុងប្រអប់ Docker ឈ្មោះ /app
WORKDIR /app

# 3. យកគ្រាប់ JAR ពីកុំព្យូទ័រយើង ទៅដាក់ក្នុងប្រអប់ Docker
COPY build/libs/titan-core-banking-0.0.1-SNAPSHOT.jar app.jar

# 4. ប្រាប់ Docker ថា App យើងប្រើច្រក 8080
EXPOSE 8080

# 5. ពាក្យបញ្ជាដើម្បី Run App (ពេលគេបើកប្រអប់)
ENTRYPOINT ["java", "-jar", "app.jar"]