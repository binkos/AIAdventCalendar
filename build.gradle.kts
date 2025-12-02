plugins {
    kotlin("jvm") version "2.1.0" apply false
}

allprojects {
    group = "com.aiadventcalendar"
    version = "1.0.0"
    
    repositories {
        mavenCentral()
        google()
    }
}

