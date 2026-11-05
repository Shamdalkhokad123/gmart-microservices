# One Dockerfile for every module. Build with, for example:
#   docker build --build-arg SERVICE=order-service -t gmart/order-service .
# (docker-compose.prod.yml does this for all five.)

# ---- build ----
FROM maven:3.9.9-eclipse-temurin-17 AS build
ARG SERVICE
WORKDIR /src
# Dependencies first, so they are cached between builds
COPY pom.xml .
COPY gmart-common/pom.xml gmart-common/
COPY api-gateway/pom.xml api-gateway/
COPY user-service/pom.xml user-service/
COPY catalog-service/pom.xml catalog-service/
COPY order-service/pom.xml order-service/
COPY payment-service/pom.xml payment-service/
COPY store-service/pom.xml store-service/
RUN mvn -B -q -pl ${SERVICE} -am dependency:go-offline
COPY . .
RUN mvn -B -q -pl ${SERVICE} -am -DskipTests package \
 && cp ${SERVICE}/target/${SERVICE}-1.0.0-SNAPSHOT.jar /app.jar

# ---- run ----
FROM eclipse-temurin:17-jre-jammy
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build /app.jar app.jar
USER app
# Use most of the container's memory limit for the heap
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
