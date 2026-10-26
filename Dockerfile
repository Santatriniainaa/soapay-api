FROM maven:3.9.12-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre-noble
WORKDIR /app
USER 10001:10001
COPY --from=build /workspace/target/soapay-api-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=5 CMD ["bash", "-c", "port=\"${PORT:-${SERVER_PORT:-8080}}\"; exec 3<>/dev/tcp/127.0.0.1/${port} && printf 'GET /actuator/health HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3 && grep -q ' 200 ' <&3"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
