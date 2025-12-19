plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.gradle.shadow)
    application
}

application {
    mainClass.set("com.aiadventcalendar.googlesheetsmcp.MainKt")
}

group = "com.aiadventcalendar.googlesheets"
version = "0.1.0"

dependencies {
    implementation(libs.mcp.kotlin.server)
    implementation(libs.kotlinx.serialization.json)
    implementation("com.google.api-client:google-api-client:2.2.0")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
    implementation("com.google.apis:google-api-services-sheets:v4-rev20220927-2.0.0")
    implementation("io.ktor:ktor-utils-jvm:2.3.12")
    
    testImplementation(libs.mcp.kotlin.client)
}

kotlin {
    jvmToolchain(libs.versions.jvm.target.get().toInt())
}

