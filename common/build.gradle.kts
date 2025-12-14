plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.1.0"
    application
}

dependencies {
    implementation("ai.koog:koog-agents:0.5.4")
    implementation("io.ktor:ktor-server-core:3.3.0")
    implementation("io.ktor:ktor-server-netty:3.3.0")
    implementation("io.ktor:ktor-server-content-negotiation:3.3.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.ktor:ktor-server-test-host:3.3.0")
}

application {
    mainClass.set("com.aiadventcalendar.common.MainKt")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

