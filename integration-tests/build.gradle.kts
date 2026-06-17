import java.time.Duration

plugins {
    groovy
    jacoco
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "org.dawid.cisowski.walletassistant"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    all {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(group = "ch.qos.logback", module = "logback-core")
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:2.0.0-M3")
        mavenBom("org.springframework.modulith:spring-modulith-bom:2.0.1")
        mavenBom("org.testcontainers:testcontainers-bom:1.21.4")
    }
}

dependencies {
    testImplementation(project(":"))
    testRuntimeOnly(project(":"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")

    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:spock")

    testImplementation("org.spockframework:spock-core:2.4-groovy-5.0")
    testImplementation("org.spockframework:spock-spring:2.4-groovy-5.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("io.rest-assured:rest-assured:6.0.0")
    testImplementation("io.rest-assured:json-path:6.0.0")

    testImplementation("org.awaitility:awaitility:4.2.0")
    testImplementation("org.awaitility:awaitility-groovy:4.2.0") {
        exclude(group = "org.codehaus.groovy", module = "groovy")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    timeout.set(Duration.ofMinutes(10))
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    jvmArgs("-Xmx1g")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    classDirectories.setFrom(
        files(project(":").layout.buildDirectory.dir("classes/java/main"))
    )
    sourceDirectories.setFrom(
        files(project(":").file("src/main/java"))
    )
    reports {
        xml.required = true
        html.required = true
    }
}
