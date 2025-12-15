plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.gradle.shadow) apply false
}

allprojects {
    group = "com.aiadventcalendar"
    version = "1.0.0"
    
    repositories {
        mavenCentral()
        google()
    }
}

