# Build the JAR outside Docker first: mvn package -DskipTests
# Then this Dockerfile just copies the prebuilt jar.

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN mkdir -p /workspace
COPY target/agent-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
