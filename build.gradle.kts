// Rewrite this spaghetti eventually

import codechicken.diffpatch.cli.DiffOperation
import codechicken.diffpatch.cli.PatchOperation
import net.minecraftforge.binarypatcher.Generator
import org.benf.cfr.reader.api.CfrDriver
import java.nio.file.Paths

buildscript {
    repositories {
        maven("https://nexus.covers1624.net/repository/maven-releases/")
        maven("https://maven.minecraftforge.net/")
        maven("https://jitpack.io/")
    }

    dependencies {
        classpath("org.benf:cfr:0.152")
        classpath("codechicken:DiffPatch:1.2.5.19")
        classpath("com.github.NepNep21:BinaryPatcher:f77712625c")
    }
}

plugins {
    java
}

group = "me.nepnep.MSA4Legacy"
version = "1.0.0"

val origDecompName = "launcherDecompTmp"
val buildDirectory: String = buildDir.canonicalPath
val origDecompDir = "$buildDirectory/$origDecompName"
val launcherJar = "$buildDirectory/launcher.jar"

repositories {
    mavenCentral()
    maven("https://maven.minecraftforge.net/")
    maven("https://jitpack.io/")
}

val installer: Configuration by configurations.creating
val shadow: Configuration by configurations.creating
val decompConfig: Configuration by configurations.creating

configurations {
    implementation.get().extendsFrom(installer, shadow)
}

dependencies {
    shadow("com.microsoft.azure:msal4j:1.11.0")
    // Shadow installer
    installer("com.github.NepNep21:BinaryPatcher:f77712625c")
    decompConfig(files(launcherJar))
}

val decompSourceSet = sourceSets.create("decomp") {
    java.srcDirs("src/decomp", "src/patches")
    // This feels stupid :sob:
    val main = sourceSets.main.get()
    compileClasspath += main.compileClasspath + decompConfig.asFileTree
    runtimeClasspath += main.runtimeClasspath + decompConfig.asFileTree
}

val taskGroup = "launcher"

open class Download : DefaultTask() {
    @Input
    lateinit var sourceUrl: String

    @OutputFile
    lateinit var target: File

    init {
        group = "launcher" // Can't use constant because of gradle stuff
    }

    @TaskAction
    fun download() {
        ant.invokeMethod("get", mapOf("src" to sourceUrl, "dest" to target))
    }
}

tasks.register<Download>("downloadLauncher") {
    sourceUrl = "https://launcher.mojang.com/v1/objects/eabbff5ff8e21250e33670924a0c5e38f47c840b/launcher.jar"

    target = File(launcherJar)
}

task("decompileLauncher") {
    group = taskGroup

    dependsOn("downloadLauncher")

    doLast {
        val options = mapOf(
            "jarfilter" to "(net.minecraft|com.mojang).*",
            "removedeadconditionals" to "true",
            "rename" to "true",
            "outputdir" to origDecompDir
        )
        CfrDriver.Builder().withOptions(options).build().analyse(listOf(launcherJar))
    }
}

val decompDir = "$projectDir/src/decomp"
val origDir = "$buildDirectory/launcherDecompOrig"
task("filterDecomp") {
    group = taskGroup

    dependsOn("decompileLauncher")

    doLast {
        File(origDecompDir).walkTopDown().forEach {
            val isBad = it.name == "summary.txt" || it.name == origDecompName
            if (isBad) {
                return@forEach
            }

            val destName = it.canonicalPath.replace(origDecompDir, "")
            val destFile = File("$decompDir/$destName")
            if (!destFile.exists()) {
                it.copyTo(destFile)
            }
            val origFile = File("$origDir/$destName")
            if (!origFile.exists()) {
                it.copyTo(origFile)
            }
        }
    }
}

val patchesDir = "$projectDir/patches"
// Does not depend on filterDecomp, set up a decompiled workspace before running this!
task("genSourceDiffs") {
    group = taskGroup
    val alphanumericRegex = "[a-zA-Z0-9]+".toRegex()

    doLast {
        DiffOperation.builder()
            .logTo(System.out)
            .aPath(Paths.get(origDir))
            .bPath(Paths.get(decompDir))
            .outputPath(Paths.get(patchesDir))
            .build()
            .operate()
        // To remove useless patches
        val patchesFile = File(patchesDir)
        patchesFile.walkTopDown().forEach { file ->
            if (file.isDirectory) {
                return@forEach
            }
            var lines = file.readLines()
            lines = lines.subList(2, lines.size) // Remove file name
            
            val hasNoAdditions = lines.none { it.startsWith('+') }
            val hasOneRemoval = lines.filter { it.startsWith('-') }.size == 1
            
            val last = lines.last()
            if (last[0] == '-' && hasNoAdditions && hasOneRemoval && !last.contains(alphanumericRegex)) { // Juust to be safe
                file.delete()
            }
        }
        patchesFile.walkBottomUp().forEach { 
            if (it.isDirectory && it.list()!!.isEmpty()) {
                it.delete()
            }
        }
    }
}

task("applyPatches") {
    group = taskGroup

    doLast {
        PatchOperation.builder()
            .logTo(System.out)
            .basePath(Paths.get(origDir))
            .patchesPath(Paths.get(patchesDir))
            .outputPath(Paths.get(decompDir))
            .build()
            .operate()
    }
}

val recompileLauncher = tasks.register<JavaCompile>("recompileLauncher") {
    group = taskGroup
    
    dependsOn("applyPatches")

    sourceCompatibility = "1.6"
    targetCompatibility = "1.6"
    
    source = decompSourceSet.java.sourceDirectories.asFileTree
    classpath = files(launcherJar, shadow.files)
    destinationDirectory.set(File("$buildDirectory/launcherRecomp"))
}.get()

tasks.getByName<Jar>("jar") {
    archiveBaseName.set("installer")
    manifest { 
        attributes("Main-Class" to "me.nepnep.msa4legacy.installer.Main")
    }
    from(installer.files.map { zipTree(it) })
}

tasks.register<Jar>("jarLauncher") {
    group = taskGroup
    dependsOn("recompileLauncher")
    from(recompileLauncher.destinationDirectory, zipTree(launcherJar), shadow.files.map { zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val outputDir = "$buildDirectory/libs"
val recompJar = "$outputDir/${project.name}-$version.jar"
task("genBinPatches") {
    group = taskGroup

    doLast {
        Generator(File("$outputDir/patches.lzma"))
            .addSet(File(launcherJar), File(recompJar), null)
            .create()
    }
}

tasks.register<JavaExec>("runLauncher") {
    group = taskGroup
    
    dependsOn("jarLauncher")
    classpath = files(recompJar)
    mainClass.set("net.minecraft.launcher.Main")
}
