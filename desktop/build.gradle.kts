plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.bundles.ktor.client)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
}

kotlin {
    jvmToolchain(libs.versions.jvm.target.get().toInt())
}

compose.desktop {
    application {
        mainClass = "com.aiadventcalendar.desktop.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "AI Advent Calendar"
            packageVersion = "1.0.0"
            macOS {
                bundleID = "com.aiadventcalendar.desktop"
            }
        }
    }
}

