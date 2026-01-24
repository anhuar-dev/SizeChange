plugins {
    `java-library`
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.17"
    id("com.gradleup.shadow") version "9.3.0"
}

group = "dev.anhuar"
version = "v1.0.2.1"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

repositories {
    mavenCentral()
    mavenLocal()
    gradlePluginPortal()

    maven("https://repo.papermc.io/repository/maven-public/")
    maven { url = uri("https://oss.sonatype.org/content/groups/public/") }
    maven { url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/") }
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://maven.enginehub.org/repo/") }
}

dependencies {
    paperweight.paperDevBundle("1.21.4-R0.1-SNAPSHOT")
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.7")
    implementation("org.mongodb:mongodb-driver-sync:5.6.0")
    implementation("com.github.Revxrsal.Lamp:common:3.1.5")
    implementation("com.github.Revxrsal.Lamp:bukkit:3.1.5")
}

tasks {

    assemble {
        dependsOn(reobfJar)
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
    javadoc {
        options.encoding = "UTF-8"
    }

    shadowJar {
        archiveClassifier.set("")
        relocate("revxrsal.commands", "dev.anhuar.sizeChange.lib.commands")
        relocate("com.mongodb", "dev.anhuar.sizeChange.lib.mongodb")
        relocate("org.bson", "dev.anhuar.sizeChange.lib.bson")
    }

    paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.REOBF_PRODUCTION

    reobfJar {
        inputJar.set(shadowJar.flatMap { it.archiveFile })
        outputJar.set(layout.buildDirectory.file("libs/${project.name}-${project.version}.jar"))
    }

}