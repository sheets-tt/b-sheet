plugins {
    id("io.micronaut.application") version "4.6.2"
    id("com.gradleup.shadow") version "8.3.9"
}

version = "0.1.0"
group = "tt.sheets"

repositories {
    mavenCentral()
}

dependencies {
    annotationProcessor("io.micronaut.data:micronaut-data-processor")
    annotationProcessor("io.micronaut:micronaut-http-validation")
    annotationProcessor("io.micronaut.security:micronaut-security-annotations")
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")
    annotationProcessor("io.micronaut.validation:micronaut-validation-processor")

    // AWS Lambda (runtime administrado java21) detrás de API Gateway HTTP API payload v2.
    implementation("io.micronaut.aws:micronaut-function-aws-api-proxy")
    implementation("io.micronaut.aws:micronaut-aws-lambda-events-serde")
    // Hooks CRaC (org.crac) que Lambda SnapStart invoca antes del snapshot / después del restore.
    implementation("io.micronaut.crac:micronaut-crac")

    implementation("io.micronaut.data:micronaut-data-jdbc")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("io.micronaut.flyway:micronaut-flyway")
    implementation("io.micronaut.security:micronaut-security-jwt")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut.validation:micronaut-validation")
    implementation("jakarta.validation:jakarta.validation-api")
    implementation("org.mindrot:jbcrypt:0.4")

    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("org.yaml:snakeyaml")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    developmentOnly("io.micronaut:micronaut-http-server-netty")

    testImplementation("io.micronaut:micronaut-http-client")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// micronaut-function-aws-api-proxy-test 4.11 (que el plugin agrega a developmentOnly) crea contextos
// anidados con micronaut-servlet-core 5.6 y hace doble bind del puerto en `./gradlew run`.
// Para desarrollo local usamos Netty (solo developmentOnly: NO entra al jar de Lambda).
// Los tests invocan directamente el handler Lambda con eventos payload v2, sin servidor HTTP.
listOf("developmentOnly", "testImplementation").forEach {
    configurations.named(it) {
        exclude(group = "io.micronaut.aws", module = "micronaut-function-aws-api-proxy-test")
    }
}

application {
    mainClass = "tt.sheets.Application"
}

java {
    sourceCompatibility = JavaVersion.toVersion("21")
    targetCompatibility = JavaVersion.toVersion("21")
}

micronaut {
    // "lambda_java" = Lambda en el runtime administrado java21 (clase handler, no custom runtime).
    runtime("lambda_java")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("tt.sheets.*")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // BD de tests: jdbc:postgresql://localhost:5432/sheets_test (sobrescribible con DB_TEST_URL).
    environment("DB_USER", System.getenv("DB_USER") ?: System.getProperty("user.name"))
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
    }
}

// Arranque local: entorno "dev" (trae JWT_SECRET de desarrollo y BD local por defecto).
tasks.named<JavaExec>("run") {
    environment("MICRONAUT_ENVIRONMENTS", System.getenv("MICRONAUT_ENVIRONMENTS") ?: "dev")
    // Postgres local (Homebrew/Postgres.app) suele usar el usuario del SO como rol.
    environment("DB_USER", System.getenv("DB_USER") ?: System.getProperty("user.name"))
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("sheets-backend")
    archiveClassifier.set("lambda")
    archiveVersion.set("")
    mergeServiceFiles()
}
