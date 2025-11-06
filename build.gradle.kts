plugins {
    kotlin("jvm") version "2.2.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("reflect"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("com.github.ajalt.clikt:clikt:4.2.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
tasks.shadowJar {
    archiveFileName.set("class-scanner-1.0-SNAPSHOT-all.jar")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "org.example.MainKt"
    }
}
