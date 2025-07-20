FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/cosmicdoc-inventory-service-0.0.1-SNAPSHOT.jar app.jar

ENV PORT=8082
ENV SPRING_PROFILES_ACTIVE=cloud

EXPOSE 8082

CMD ["java", "-Dserver.port=8082", "-Dspring.profiles.active=cloud", "-XX:InitialRAMPercentage=50", "-XX:MaxRAMPercentage=70", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
