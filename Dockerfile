FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn -B -pl citylife-core-service -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/citylife-core-service/target/citylife-core-service-0.0.1-SNAPSHOT.jar /app/app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
