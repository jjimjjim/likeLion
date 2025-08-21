# ----- Build stage -----
FROM gradle:8.8-jdk21 AS builder
WORKDIR /app
COPY . .
RUN gradle clean build -x test

# ----- Runtime stage -----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar /app/app.jar
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENV SERVER_PORT=8080
EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -Dserver.port=$SERVER_PORT -jar /app/app.jar"]



