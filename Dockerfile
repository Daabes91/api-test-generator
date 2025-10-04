# Build stage: compile the Spring Boot app and produce the runnable jar
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY .mvn ./.mvn
COPY mvnw .
COPY src ./src
ENV MAVEN_CONFIG=""
RUN ./mvnw -q dependency:go-offline \
    && ./mvnw -q -DskipTests package spring-boot:repackage

# Runtime stage: lightweight JRE image serving the app
FROM eclipse-temurin:17
WORKDIR /app
COPY --from=build /workspace /app
COPY --from=build /root/.m2 /root/.m2
RUN chmod +x mvnw \
    && ln -sf /app/target/api-1.0-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
