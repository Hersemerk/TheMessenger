val kotlin_version = "1.3.72"
val ktor_version = "1.3.2"
val logbackVersion = "1.2.3"

plugins {
    kotlin("jvm") version "1.3.72"
    application
}

group = "org.example"
version = "1.2-SELFSHOT"

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib-jdk8:$kotlin_version"))
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-html-builder:$ktor_version")
    implementation("io.ktor:ktor-websockets:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlin_version")
    implementation("io.ktor:ktor-serialization:$ktor_version")
    implementation("io.ktor:ktor-auth:$ktor_version")
    implementation("org.litote.kmongo:kmongo:4.0.2")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
}

application {
    mainClassName = "io.ktor.server.netty.EngineMain"
}

tasks.create("stage") {
    dependsOn(tasks.getByName("installDist"))
}