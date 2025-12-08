plugins {
    java
    `java-library`
    `maven-publish`
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
    mavenLocal()
    mavenCentral()
}

var springBootVersion = "3.5.7"
var springAIVersion = "1.1.0"

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
    testImplementation("com.github.jsqlparser:jsqlparser:4.9")
}

// Exclude Spring Boot's default Logback starter to avoid conflicts with Log4j2
configurations.all {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:$springAIVersion")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Configure Spring Boot to not create a bootJar by default.
// Instead, use the plain JAR as the main artifact for library consumption.
tasks {
    bootJar {
        enabled = false
    }
    
    jar {
        enabled = true
    }
    
    // Ensure the plain jar is the default artifact
    build {
        dependsOn(jar)
    }
}

// Configure Maven publishing for the plain JAR
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "org.javai.springai"
            artifactId = "SpringAIActions"
            version = "0.0.1-SNAPSHOT"
        }
    }
}
