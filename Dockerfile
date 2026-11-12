FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace/backend
COPY backend/pom.xml ./pom.xml
COPY backend/src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S solar && adduser -S solar -G solar
USER solar
WORKDIR /app
COPY --from=build /workspace/backend/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
