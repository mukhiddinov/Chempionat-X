FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy the built jar file
COPY target/*.jar app.jar

# Expose Spring Boot default port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
