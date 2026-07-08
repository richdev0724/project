plugins {
    `java`
    id("org.springframework.boot") version "3.5.3"
    id("io.spring.dependency-management") version "1.1.6"
    id("org.liquibase.gradle") version "2.2.2"
    id("com.google.protobuf") version "0.9.6"
}

group = "com.marketmono"
version = "0.0.1-SNAPSHOT"

`java` {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("io.netty:netty-bom:4.2.7.Final")
    }
}

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux") // WebClient for reactive HTTP
    implementation("org.springframework.boot:spring-boot-starter-websocket") // STOMP WebSocket support
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    
    // Kafka + Protobuf
    implementation("org.springframework.kafka:spring-kafka")
    implementation("com.google.protobuf:protobuf-java:4.31.1")
    
    // JWT (JJWT library)
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    
    // Database & Migrations
    implementation("org.postgresql:postgresql:42.7.8")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.liquibase:liquibase-core:4.29.2")
    liquibaseRuntime("org.liquibase:liquibase-core:4.29.2")
    liquibaseRuntime("org.postgresql:postgresql:42.7.8")
    liquibaseRuntime("info.picocli:picocli:4.7.6")
    implementation("net.javacrumbs.shedlock:shedlock-spring:7.7.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.7.0")
    
    // Caching
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.1")
    
    // Utilities
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    implementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.module:jackson-module-parameter-names")
    
    // Rate Limiting (Guava)
    implementation("com.google.guava:guava:33.3.1-jre")
    
    // TOTP 2FA (Time-based One-Time Password)
    implementation("dev.samstevens.totp:totp:1.7.1")
    
    // Stripe Java SDK
    implementation("com.stripe:stripe-java:30.0.0")
    implementation("com.google.code.gson:gson:2.13.1") // Required for Stripe's getRawJsonObject()
    
    // Fuzzy String Matching (Apache Commons Text)
    implementation("org.apache.commons:commons-text:1.13.0")
    
    // Circuit Breaker & Resilience (Resilience4j)
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    implementation("io.github.resilience4j:resilience4j-reactor:2.2.0")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.2.0")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    
    // Monitoring & Observability
    implementation("io.micrometer:micrometer-registry-prometheus")
    
    // API Documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
    
    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:postgresql:1.21.2")
    testImplementation("org.testcontainers:junit-jupiter:1.21.2")
    testImplementation("org.testcontainers:kafka:1.21.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

fun liquibaseCliArgs(command: String): List<String> {
    val changelogFile = project.findProperty("liquibaseCommandChangeLogFile")
        ?: "db/changelog/db.changelog-master.yaml"
    val resourcePath = project.findProperty("liquibaseCommandResourcePath")
        ?: "src/main/resources"
    val url = project.findProperty("liquibaseCommandUrl") ?: ""
    val username = project.findProperty("liquibaseCommandUsername") ?: ""
    val password = project.findProperty("liquibaseCommandPassword") ?: ""
    val logLevel = project.findProperty("liquibaseCommandLogLevel") ?: "info"

    return listOf(
        "--classpath=$resourcePath",
        "--changeLogFile=$changelogFile",
        "--url=$url",
        "--username=$username",
        "--password=$password",
        "--logLevel=$logLevel",
        command
    )
}

tasks.register<JavaExec>("liquibaseValidateValidationDb") {
    group = "Liquibase validation"
    description = "Validate the Liquibase changelog against an explicit validation database connection."
    classpath = configurations.named("liquibaseRuntime").get()
    mainClass.set("liquibase.integration.commandline.Main")
    args(liquibaseCliArgs("validate"))
}

tasks.register<JavaExec>("liquibaseUpdateValidationDb") {
    group = "Liquibase validation"
    description = "Run the Liquibase changelog against an explicit validation database connection."
    classpath = configurations.named("liquibaseRuntime").get()
    mainClass.set("liquibase.integration.commandline.Main")
    args(liquibaseCliArgs("update"))
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("--enable-preview")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
    options.compilerArgs.add("--enable-preview")
    options.isIncremental = true
}

// BootRun optimizations for faster startup
tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
    // Use JVM args for faster startup
    jvmArgs = listOf(
        "-XX:+UseParallelGC",
        "-XX:MaxGCPauseMillis=100",
        "-XX:+UseStringDeduplication",
        "-Djava.awt.headless=true",
        "--enable-preview"
    )
    // Forward all parent process environment variables to the forked JVM
    // This ensures KAFKA_ENABLED, KAFKA_PASSWORD, etc. are available at runtime
    environment(System.getenv())
}

// Protobuf code generation configuration
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.31.1"
    }
}
