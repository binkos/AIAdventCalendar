plugins {
    kotlin("jvm") version "2.1.0"
    application
}

group = "com.aichallenge"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("ai.koog:koog-agents:0.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.aichallenge.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

