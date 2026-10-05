# syntax=docker/dockerfile:1
# Imagen de AWS Lambda (contenedor) para el backend Micronaut.
# El jar se compila en la arquitectura del host (BUILDPLATFORM) y solo la capa final es arm64/amd64,
# así que construir para arm64 desde x86 no emula la compilación de Java.

FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon -q dependencies > /dev/null
COPY src src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon -q shadowJar -x test

# Imagen base oficial de Lambda: trae el runtime java25 y el Runtime Interface Emulator para pruebas locales.
FROM public.ecr.aws/lambda/java:25
COPY --from=build /src/build/libs/sheets-backend-lambda.jar ${LAMBDA_TASK_ROOT}/lib/
CMD ["io.micronaut.function.aws.proxy.payload2.APIGatewayV2HTTPEventFunction::handleRequest"]
