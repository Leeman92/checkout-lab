# Development stage
FROM maven:3.9-eclipse-temurin-25 AS base

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src

EXPOSE 8080

CMD ["mvn", "spring-boot:run"]

FROM base AS development

COPY ./mvnw .
COPY .mvn/ .mvn/
RUN chmod +x mvnw

# Build stage
FROM base AS build

RUN mvn clean package -DskipTests


# Production stage
FROM eclipse-temurin:25-jre AS production

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
