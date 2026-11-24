FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml QuickStartServer.java ./
RUN mvn -B -ntp package dependency:copy-dependencies
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /src/target/classes /app/classes
COPY --from=build /src/target/dependency /app/lib
COPY index.html report.prepdj /app/
CMD ["java","--add-modules","jdk.httpserver","-cp","/app/classes:/app/lib/*","QuickStartServer"]
