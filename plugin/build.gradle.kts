plugins {
    id("xyz.jpenilla.run-paper") version "2.3.0"
    id("io.github.goooler.shadow") version "8.1.2"
    id("io.papermc.paperweight.userdev") version "1.7.1"
}

version = project.property("project_version") as String
group = "beta-${project.property("project_group")}"

repositories {
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://repo.papermc.io/repository/maven-public/") // Paper, FAWE
    maven("https://maven.enginehub.org/repo") // FAWE? lin bus fuck you
    maven("https://repo.codemc.io/repository/maven-public/") // CommandAPI, NBT API
    maven("https://mvn.lumine.io/repository/maven-public/") // MythicMobs
    maven("https://repo.codemc.io/repository/maven-releases/") // packetevents
}

dependencies {
    // as a reminder, api means internal references in said library are visible to me,
    // while implementation means only components defined in the library i am referencing are visible
    // (and therefore nothing more) ex. using api notation for `command-api` would expose NMS,
    // while implementation notation would not (therefore only exposing `command-api`)
    paperweight.paperDevBundle("1.21-R0.1-SNAPSHOT")

    // kotlin and internal
    api(kotlin("reflect"))
    api("io.insert-koin:koin-core:3.5.6") // cant load koin with paper
    implementation(project(":common"))

    // minecraft

    // required
    compileOnly("dev.jorel:commandapi-bukkit-shade-mojang-mapped:9.5.1") // CommandAPI
    implementation(platform("com.intellectualsites.bom:bom-newest:1.47")) // FAWE Ref: https://github.com/IntellectualSites/bom

    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Core") // FAWE
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Bukkit") // FAWE

    // support / soft-depend
    compileOnly("io.lumine:Mythic-Dist:5.6.1") // MythicMobs API
}

java{
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks {
    processResources {
        filesMatching("paper-plugin.yml") {
            expand(mapOf("version" to project.version))
        }
    }

    shadowJar {
        archiveFileName = "Instantiated.jar"
        // from(sourceSets.main.get().output)
        minimize {
            include(project(":common"))
            exclude("kotlin/")
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib"))
            exclude(dependency("org.jetbrains.kotlin:kotlin-reflect"))
        }
        minimize()
    }

    assemble {
        dependsOn(shadowJar)
    }

    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin"s jar (or shadowJar if present) will be used automatically.
        minecraftVersion(project.property("minecraft_version").toString())
    }
}