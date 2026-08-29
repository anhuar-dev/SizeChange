plugins {
    id("java")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.17"
    id("com.gradleup.shadow") version "9.3.0"
}

group = "dev.anhuar"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
    gradlePluginPortal()

    maven("https://repo.papermc.io/repository/maven-public/")
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://maven.enginehub.org/repo/") }
    maven { url = uri("https://repo.extendedclip.com/releases/") }
}

dependencies {
    paperweight.paperDevBundle("1.21.4-R0.1-SNAPSHOT")

    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.7")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.0")

    compileOnly("com.google.code.gson:gson:2.10.1")
    compileOnly("org.mongodb:mongodb-driver-sync:4.11.1")
    compileOnly("org.xerial:sqlite-jdbc:3.47.1.0")

    compileOnly("me.clip:placeholderapi:2.11.6")

    // Shaded + relocated (bStats requires the relocation, see shadowJar below).
    implementation("org.bstats:bstats-bukkit:3.1.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        relocate("org.bstats", "dev.anhuar.bstats")
    }

    assemble {
        dependsOn(shadowJar)
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    processResources {
        filteringCharset = "UTF-8"
    }
}