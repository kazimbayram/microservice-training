FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY mvnw ./
COPY .mvn/ ./.mvn/
COPY pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src/main/ ./src/main/
RUN ./mvnw -B clean package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8082
CMD ["java", "-jar", "app.jar"]
