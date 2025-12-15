plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.application)
}

dependencies {
    implementation(libs.koog.agents)
    implementation(libs.bundles.ktor.server)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sqlite.jdbc)
    implementation(libs.ktor.server.sse)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
}

application {
    mainClass.set("com.aiadventcalendar.common.MainKt")
}

kotlin {
    jvmToolchain(libs.versions.jvm.target.get().toInt())
}

tasks.test {
    useJUnitPlatform()
}

