# Build stage: compile the Spring Boot app and produce the runnable jar
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY .mvn ./.mvn
COPY mvnw .
COPY src ./src
ENV MAVEN_CONFIG=""
RUN ./mvnw -q -DskipTests package spring-boot:repackage

# Runtime stage: lightweight JRE image serving the app
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/mvnw ./mvnw
COPY --from=build /workspace/.mvn ./.mvn
COPY --from=build /workspace/target/api-1.0-SNAPSHOT.jar app.jar
RUN chmod +x mvnw
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
