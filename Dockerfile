FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/target/shift-scheduler-bot-1.0.0-shaded.jar /app/app.jar
ENV TZ=Europe/Kyiv
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
