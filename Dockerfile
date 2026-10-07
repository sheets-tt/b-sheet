# syntax=docker/dockerfile:1
# Imagen de AWS Lambda (contenedor) para el backend Micronaut.
# El jar se compila en la arquitectura del host (BUILDPLATFORM) y solo la capa final es arm64/amd64,
# así que construir para arm64 desde x86 no emula la compilación de Java.

FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests

# Imagen base oficial de Lambda: trae el runtime java25 y el Runtime Interface Emulator para pruebas locales.
FROM public.ecr.aws/lambda/java:25
COPY --from=build /src/target/sheets-backend-lambda.jar ${LAMBDA_TASK_ROOT}/lib/
CMD ["io.micronaut.function.aws.proxy.payload2.APIGatewayV2HTTPEventFunction::handleRequest"]
