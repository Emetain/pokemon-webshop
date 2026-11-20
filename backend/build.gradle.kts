// Spring Boot 4.1 (checked on Maven Central on 2026-11-15: 4.1.2, free support until 2027-07-31). Kotlin is the
// version Spring Boot 4.1.2 is built and tested with.
plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.emetain.pokemonwebshop"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    // Only the health endpoint, for the deploy check. See management in application.yml.
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.security:spring-security-crypto")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.stripe:stripe-java:29.2.0")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0")

    runtimeOnly("com.h2database:h2")
    // The H2 console of the dev profile. Switched off in prod.
    runtimeOnly("org.springframework.boot:spring-boot-h2console")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

// Hibernate needs open classes to create lazy-loading proxies.
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()

    // The failed tests once more at the very end, so in a long CI log their names sit right above Gradle's summary.
    val failures = mutableListOf<String>()
    afterTest(
        KotlinClosure2<TestDescriptor, TestResult, Unit>({ test, result ->
            if (result.resultType == TestResult.ResultType.FAILURE) {
                failures += "${test.className?.substringAfterLast('.')} > ${test.displayName}"
            }
        }),
    )
    afterSuite(
        KotlinClosure2<TestDescriptor, TestResult, Unit>({ suite, _ ->
            if (suite.parent == null && failures.isNotEmpty()) {
                println("\nFailed tests:\n" + failures.joinToString("\n") { "  - $it" })
            }
        }),
    )
}

// Only the runnable jar: backend/Dockerfile copies build/libs/*.jar to one file.
tasks.named<Jar>("jar") {
    enabled = false
}
