FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY target/tf-reader.jar /app/app.jar
EXPOSE 8103
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
