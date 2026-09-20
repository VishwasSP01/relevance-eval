plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    api(project(":core"))

    implementation("co.elastic.clients:elasticsearch-java:8.15.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("jakarta.json:jakarta.json-api:2.1.3")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("org.testcontainers:testcontainers:1.20.1")
    testImplementation("org.testcontainers:elasticsearch:1.20.1")
    testImplementation("org.testcontainers:junit-jupiter:1.20.1")
}

tasks.test {
    useJUnitPlatform()
}
