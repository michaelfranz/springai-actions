plugins {
    java
    id("org.springframework.boot") version "3.5.7"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "org.javai.springai"
version = "0.0.1-SNAPSHOT"
description = "Spring AI Actions"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

var springAiVersion = "1.1.0"
var springBootVersion = "3.5.7"
var springAIVersion = "1.0.3"

dependencies {

    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation(platform("org.springframework.ai:spring-ai-bom:$springAIVersion"))
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-log4j2")
    implementation("org.liquibase:liquibase-core")
    runtimeOnly("org.postgresql:postgresql")
    // Native DNS resolver for macOS (Apple Silicon) to avoid UnsatisfiedLinkError
    runtimeOnly("io.netty:netty-resolver-dns-native-macos") {
        artifact {
            classifier = "osx-aarch_64"
        }
    }
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("com.h2database:h2")
    testImplementation("org.apache.commons:commons-csv:1.10.0")
}

// Exclude Spring Boot's default Logback starter to avoid conflicts with Log4j2
configurations.all {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:$springAiVersion")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
