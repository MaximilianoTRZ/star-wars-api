FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN ./mvnw dependency:go-offline -q

COPY src src
RUN ./mvnw -B package -DskipTests -q && \
    cp target/star-wars-api-*.jar target/app.jar && \
    java -Djarmode=tools -jar target/app.jar extract --layers --launcher --destination target/extracted

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN addgroup --system app && adduser --system --ingroup app --uid 1001 app
WORKDIR /app
USER app

COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/application/ ./

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
