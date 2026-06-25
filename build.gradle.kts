import java.time.Instant

plugins {
    kotlin("jvm") version "2.3.21"
    id("com.gradleup.shadow") version "9.4.2"
}

val generateGitProperties by tasks.registering {
    group = "build"
    description = "Generates git.properties file with commit information"

    val outputFile = layout.buildDirectory.file("generated/resources/git/git.properties")

    inputs.property("git.head", providers.exec {
        commandLine("git", "rev-parse", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.map { it.trim() }.orElse("unknown"))

    outputs.file(outputFile)

    doLast {
        fun runGit(command: String): String = providers.exec {
            commandLine("git", *command.split(" ").toTypedArray())
            isIgnoreExitValue = true
        }.standardOutput.asText.map { it.trim() }.getOrElse("unknown")

        val props = mapOf(
            "git.commit.id" to runGit("rev-parse HEAD"),
            "git.commit.id.abbrev" to runGit("rev-parse --short HEAD"),
            "git.commit.message.short" to runGit("log -1 --pretty=%s"),
            "git.commit.time" to runGit("log -1 --pretty=%cI"),
            "git.branch" to runGit("rev-parse --abbrev-ref HEAD"),
            "git.build.time" to Instant.now().toString(),
            "git.dirty" to (runGit("status --porcelain").isNotBlank()).toString()
        )

        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(props.entries.joinToString("\n") { "${it.key}=${it.value}" })
        }
    }
}

val gitCommitShort = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim() }.getOrElse("unknown")

sourceSets {
    main {
        resources {
            srcDir(layout.buildDirectory.dir("generated/resources/git"))
        }
    }
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://repo.tcoded.com/releases")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("org.slf4j:slf4j-api:2.0.9")
    compileOnly("me.clip:placeholderapi:2.12.2")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    // VaultUnlockedAPI not used — EconomyManager uses VaultAPI 1.7.1 only
    implementation("dev.triumphteam:triumph-gui:3.1.13")
    implementation("com.mysql:mysql-connector-j:9.7.0")
    implementation("com.tcoded:FoliaLib:0.5.1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("com.zaxxer:HikariCP:5.1.0")

    // ──────────────────────────────────────
    //  Test dependencies
    // ──────────────────────────────────────
    testImplementation("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.3.21")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

kotlin {
    jvmToolchain(21)
}

tasks.shadowJar {
    val packageLib = "tech.qhuyy.hqngOrder.libs"
    archiveClassifier.set("")
    archiveFileName.set("${project.name}-${project.version}+${gitCommitShort}.jar")

    relocate("com.mysql", "$packageLib.mysql")
    relocate("com.tcoded", "$packageLib.tcoded")
    relocate("com.zaxxer", "$packageLib.zaxxer")
    relocate("dev.triumphteam.gui", "tech.qhuyy.hqngOrder.gui")
    relocate("kotlin", "$packageLib.kotlin") {
        exclude("kotlin/kotlin.kotlin_builtins")
        exclude("kotlin/reflect/reflect.kotlin_builtins")
        exclude("META-INF/kotlin*")
    }

    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
    exclude("META-INF/MANIFEST.MF")
    exclude("META-INF/NOTICE*")
    exclude("META-INF/versions/**")
    exclude("META-INF/maven/**")
    exclude("META-INF/proguard/**")
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    processResources {
        dependsOn(generateGitProperties)
        val props = mapOf("" +
                "version" to version,
            "gitCommit" to gitCommitShort
        )
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    test {
        useJUnitPlatform()
    }

    register("printGitInfo") {
        group = "help"
        description = "Prints current git information"
        doLast {
            println("\n=== Git Information ===")
            file(layout.buildDirectory.file("generated/resources/git/git.properties").get().asFile)
                .takeIf { it.exists() }
                ?.readLines()
                ?.forEach { println(it) }
                ?: println("Git properties not generated yet. Run 'build' first.")
        }
    }
}
