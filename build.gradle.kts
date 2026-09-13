plugins {
    id("dev.kikugie.loom-back-compat")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = property("mod.id") as String

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
}

java {
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava

    toolchain {
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

// On 26.1+ the unobfuscated loom variant re-attaches the raw source dirs, bypassing
// Stonecutter's preprocessed sources; re-assert them for every node after evaluation.
// Harmless for the remap-loom nodes, which already point at the same directories.
afterEvaluate {
    val generatedMain = file("build/generated/stonecutter/main/java")

    sourceSets {
        main {
            java { setSrcDirs(listOf("src/main/java", generatedMain)) }
        }
    }
    tasks.named("compileJava") { dependsOn("stonecutterGenerate") }
}

tasks {
    processResources {
        val props = buildMap<String, String> {
            fun register(key: String, property: String) {
                val value: String = sc.properties[property]
                inputs.property(key, value)
                put(key, value)
            }
            register("id", "mod.id")
            register("name", "mod.name")
            register("minecraft", "mod.mc_compat")
            val modVersion: String = sc.properties["mod.version"]
            inputs.property("mod.version", modVersion)
            inputs.property("mc.version", sc.current.version)
            put("version", "$modVersion+${sc.current.version}")
        }
        filesMatching("fabric.mod.json") { expand(props) }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Copies the built mod jar into build/libs/<mod version>/"
        from(loomx.modJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
    }
}
