FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring --home-dir /app spring

COPY hello-grpc-provider/target/hello-grpc-provider-*.jar /app/app.jar

USER spring:spring

EXPOSE 8081
EXPOSE 9090

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
