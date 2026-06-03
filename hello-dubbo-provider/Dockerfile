FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring --home-dir /app spring

COPY hello-dubbo-provider/target/hello-dubbo-provider-*.jar /app/app.jar

USER spring:spring

EXPOSE 8082
EXPOSE 50051

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
