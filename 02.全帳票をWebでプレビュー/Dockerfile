FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /work
COPY pom.xml ./
COPY sample-app/pom.xml sample-app/pom.xml
COPY sample-app/src sample-app/src
COPY resources /opt/test-resources
ENV REPORTS_RESOURCE_ROOT=/opt/test-resources
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp verify

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S reports && adduser -S -G reports reports
WORKDIR /opt/reports
COPY --from=build /work/sample-app/target/reports-web-sample-0.1.0.jar app.jar
COPY resources resources
USER reports
ENV JAVA_TOOL_OPTIONS="-Xms32m -Xmx256m -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=64m -XX:ActiveProcessorCount=2 -Duser.timezone=Asia/Tokyo"
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
