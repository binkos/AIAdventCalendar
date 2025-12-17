plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.gradle.shadow)
    application
}

application {
    mainClass.set("com.aiadventcalendar.mcp.MainKt")
}

group = "com.aiadventcalendar.weather"
version = "0.1.0"


dependencies {
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.mcp.kotlin.server)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.client.cio)
    implementation(libs.slf4j.simple)
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")

    testImplementation(libs.mcp.kotlin.client)
}

kotlin {
    jvmToolchain(17)
}