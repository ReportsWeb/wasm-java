FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q dependency:copy-dependencies -DoutputDirectory=/out/lib
COPY QuickStartServer.java .
RUN javac --add-modules jdk.httpserver -encoding UTF-8 -cp "/out/lib/*" -d /out/classes QuickStartServer.java

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S reports && adduser -S -G reports reports
WORKDIR /app
COPY --from=build /out /app
COPY index.html report.prepdj /app/
USER reports
CMD ["java", "--add-modules", "jdk.httpserver", "-cp", "/app/classes:/app/lib/*", "QuickStartServer"]
