FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
ENV PORT=8080
EXPOSE ${PORT}
ENTRYPOINT java -Xms64m -Xmx256m -Xss256k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom -jar app.jar --spring.profiles.active=railway --server.port=${PORT}
