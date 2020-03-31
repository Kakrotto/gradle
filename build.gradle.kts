/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.gradle.build.BuildReceipt
import org.gradle.gradlebuild.ProjectGroups.implementationPluginProjects
import org.gradle.gradlebuild.ProjectGroups.javaProjects
import org.gradle.gradlebuild.ProjectGroups.kotlinJsProjects
import org.gradle.gradlebuild.ProjectGroups.pluginProjects
import org.gradle.gradlebuild.ProjectGroups.publicJavaProjects
import org.gradle.gradlebuild.UpdateAgpVersions
import org.gradle.gradlebuild.UpdateBranchStatus
import org.gradle.gradlebuild.buildquality.incubation.IncubatingApiAggregateReportTask
import org.gradle.gradlebuild.buildquality.incubation.IncubatingApiReportTask
import org.gradle.plugins.buildtypes.projectProperty
import org.gradle.plugins.install.Install

plugins {
    `java-base`
    gradlebuild.`build-types`
    gradlebuild.`ci-reporting`
    gradlebuild.security
    gradlebuild.install
}

buildscript {
    dependencies {
        constraints {
            classpath("xerces:xercesImpl:2.12.0") {
                // it's unclear why we don't get this version directly from buildSrc constraints
                because("Maven Central and JCenter disagree on version 2.9.1 metadata")
            }
        }
    }
}

defaultTasks("assemble")

base.archivesBaseName = "gradle"

// Basic compile and check lifecycle tasks
subprojects {
    if (project in javaProjects) {
        tasks.register("compileAllBuild") {
            description = "Initialize CI Pipeline by priming the cache before fanning out"
            dependsOn(":createBuildReceipt", "compileAll")
            projectProperty("ignoreIncomingBuildReceipt" to true)
        }

        tasks.register("sanityCheck") {
            description = "Run all basic checks (without tests) - to be run locally and on CI for early feedback"
            dependsOn(
                "compileAll", ":docs:checkstyleApi", "codeQuality", ":allIncubationReportsZip",
                ":distributions:checkBinaryCompatibility", ":docs:javadocAll",
                ":architectureTest:test", ":toolingApi:toolingApiShadedJar")
        }
    }
}

// Test lifecycle tasks that correspond to CIBuildModel.TestType (see .teamcity/Gradle_Check/model/CIBuildModel.kt)
subprojects {
    if (project in javaProjects) {
        tasks.register("quickTest") {
            description = "Run all unit, integration and cross-version (against latest release) tests in embedded execution mode"
            dependsOn("test", "integTest", "crossVersionTest")
        }

        tasks.register("platformTest") {
            description = "Run all unit, integration and cross-version (against latest release) tests in forking execution mode"
            dependsOn("test", "forkingIntegTest", "forkingCrossVersionTest")
            projectProperty("testVersions" to "partial")
        }

        tasks.register("quickFeedbackCrossVersionTest") {
            description = "Run cross-version tests against a limited set of versions"
            dependsOn("quickFeedbackCrossVersionTests")
            projectProperty("useAllDistribution" to true)
        }

        tasks.register("allVersionsCrossVersionTest") {
            description = "Run cross-version tests against all released versions (latest patch release of each)"
            dependsOn("allVersionsCrossVersionTests")
            projectProperty("testVersions" to "all")
            projectProperty("useAllDistribution" to true)
        }

        tasks.register("allVersionsIntegMultiVersionTest") {
            description = "Run all multi-version integration tests with all version to cover"
            dependsOn("integMultiVersionTest")
            projectProperty("testVersions" to "all")
            projectProperty("useAllDistribution" to true)
        }

        tasks.register("parallelTest") {
            description = "Run all integration tests in parallel execution mode: each Gradle execution started in a test run with --parallel"
            dependsOn("parallelIntegTest")
        }

        tasks.register("noDaemonTest") {
            description = "Run all integration tests in no-daemon execution mode: each Gradle execution started in a test forks a new daemon"
            dependsOn("noDaemonIntegTest")
            projectProperty("useAllDistribution" to true)
        }

        tasks.register("instantTest") {
            description = "Run all integration tests with instant execution"
            dependsOn("instantIntegTest")
        }

        tasks.register("vfsRetentionTest") {
            description = "Run all integration tests with vfs retention enabled"
            dependsOn("vfsRetentionIntegTest")
        }

        tasks.register("soakTest") {
            description = "Run all soak tests defined in the :soak subproject"
            dependsOn(":soak:soakIntegTest")
            projectProperty("testVersions" to "all")
        }

        tasks.register("forceRealizeDependencyManagementTest") {
            description = "Runs all integration tests with the dependency management engine in 'force component realization' mode"
            dependsOn("integForceRealizeTest")
        }
    }
}

tasks.register("packageBuild") {
    description = "Build production distros and smoke test them"
    dependsOn(":verifyIsProductionBuildEnvironment", ":distributions:buildDists",
        ":distributions:integTest", ":docs:check", ":docs:checkSamples")
}

subprojects {
    plugins.withId("gradlebuild.publish-public-libraries") {
        tasks.register("promotionBuild") {
            description = "Build production distros, smoke test them and publish"
            dependsOn(":verifyIsProductionBuildEnvironment", ":distributions:buildDists",
                ":distributions:integTest", ":docs:check", "publish")
        }
    }
}

allprojects {
    group = "org.gradle"

    repositories {
        maven {
            name = "Gradle libs"
            url = uri("https://repo.gradle.org/gradle/libs")
        }
        maven {
            name = "kotlinx"
            url = uri("https://kotlin.bintray.com/kotlinx/")
        }
        maven {
            name = "kotlin-dev"
            url = uri("https://dl.bintray.com/kotlin/kotlin-dev")
        }
        maven {
            name = "kotlin-eap"
            url = uri("https://dl.bintray.com/kotlin/kotlin-eap")
        }
    }

    // patchExternalModules lives in the root project - we need to activate normalization there, too.
    normalization {
        runtimeClasspath {
            ignore("org/gradle/build-receipt.properties")
        }
    }
}

apply(plugin = "gradlebuild.cleanup")
apply(plugin = "gradlebuild.buildscan")
apply(plugin = "gradlebuild.build-version")
apply(from = "gradle/dependencies.gradle")
apply(plugin = "gradlebuild.minify")
apply(from = "gradle/test-dependencies.gradle")
apply(plugin = "gradlebuild.wrapper")
apply(plugin = "gradlebuild.ide")
apply(plugin = "gradlebuild.quick-check")
apply(plugin = "gradlebuild.update-versions")
apply(plugin = "gradlebuild.dependency-vulnerabilities")
apply(plugin = "gradlebuild.add-verify-production-environment-task")
apply(plugin = "gradlebuild.generate-subprojects-info")

// https://github.com/gradle/gradle-private/issues/2463
apply(from = "gradle/remove-teamcity-temp-property.gradle")

allprojects {
    apply(plugin = "gradlebuild.dependencies-metadata-rules")
}

subprojects {
    version = rootProject.version

    if (project in javaProjects) {
        apply(plugin = "gradlebuild.java-projects")
    }

    if (project in publicJavaProjects) {
        apply(plugin = "gradlebuild.public-java-projects")
    }

    apply(from = "$rootDir/gradle/shared-with-buildSrc/code-quality-configuration.gradle.kts")

    if (project !in kotlinJsProjects) {
        apply(plugin = "gradlebuild.task-properties-validation")
    }
}

val runtimeUsage = objects.named(Usage::class.java, Usage.JAVA_RUNTIME)

val coreRuntime by configurations.creating {
    attributes.attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage)
    isCanBeResolved = true
    isCanBeConsumed = false
    isVisible = false
}

val coreRuntimeExtensions by configurations.creating {
    attributes.attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage)
    isCanBeResolved = true
    isCanBeConsumed = false
    isVisible = false
}

val externalModules by configurations.creating {
    isVisible = false
}

/**
 * Combines the 'coreRuntime' with the patched external module jars
 */
val runtime by configurations.creating {
    isVisible = false
    extendsFrom(coreRuntime)
}

val gradlePlugins by configurations.creating {
    isVisible = false
}

val testRuntime by configurations.creating {
    extendsFrom(coreRuntime)
    extendsFrom(gradlePlugins)
}

// TODO: These should probably be all collapsed into a single variant
configurations {
    create("gradleApiMetadataElements") {
        isVisible = false
        isCanBeResolved = false
        isCanBeConsumed = true
        extendsFrom(coreRuntime)
        extendsFrom(gradlePlugins)
        attributes.attribute(Attribute.of("org.gradle.api", String::class.java), "metadata")
    }
}
configurations {
    create("gradleApiRuntimeElements") {
        isVisible = false
        isCanBeResolved = false
        isCanBeConsumed = true
        extendsFrom(externalModules)
        extendsFrom(gradlePlugins)
        attributes.attribute(Attribute.of("org.gradle.api", String::class.java), "runtime")
    }
}
configurations {
    create("gradleApiCoreElements") {
        isVisible = false
        isCanBeResolved = false
        isCanBeConsumed = true
        extendsFrom(coreRuntime)
        attributes.attribute(Attribute.of("org.gradle.api", String::class.java), "core")
    }
}
configurations {
    create("gradleApiCoreExtensionsElements") {
        isVisible = false
        isCanBeResolved = false
        isCanBeConsumed = true
        extendsFrom(coreRuntime)
        extendsFrom(coreRuntimeExtensions)
        attributes.attribute(Attribute.of("org.gradle.api", String::class.java), "core-ext")
    }
}
configurations {
    create("gradleApiPluginElements") {
        isVisible = false
        isCanBeResolved = false
        isCanBeConsumed = true
        extendsFrom(gradlePlugins)
        attributes.attribute(Attribute.of("org.gradle.api", String::class.java), "plugins")
    }
}
configurations {
    create("gradleApiReceiptElements") {
        isVisible = false
        isCanBeResolved = false
        isCanBeConsumed = true
        attributes.attribute(Attribute.of("org.gradle.api", String::class.java), "build-receipt")

        // TODO: Update BuildReceipt to retain dependency information by using Provider
        val createBuildReceipt = tasks.named("createBuildReceipt", BuildReceipt::class.java)
        val receiptFile = createBuildReceipt.map {
            it.receiptFile
        }
        outgoing.artifact(receiptFile) {
            builtBy(createBuildReceipt)
        }
    }
}

extra["allTestRuntimeDependencies"] = testRuntime.allDependencies

dependencies {

    coreRuntime(project(":launcher"))
    coreRuntime(project(":runtimeApiInfo"))
    coreRuntime(project(":wrapper"))
    coreRuntime(project(":installationBeacon"))
    coreRuntime(project(":kotlinDsl"))

    pluginProjects.forEach { gradlePlugins(it) }
    implementationPluginProjects.forEach { gradlePlugins(it) }

    gradlePlugins(project(":workers"))
    gradlePlugins(project(":dependencyManagement"))
    gradlePlugins(project(":testKit"))

    coreRuntimeExtensions(project(":dependencyManagement")) //See: DynamicModulesClassPathProvider.GRADLE_EXTENSION_MODULES
    coreRuntimeExtensions(project(":instantExecution"))
    coreRuntimeExtensions(project(":pluginUse"))
    coreRuntimeExtensions(project(":workers"))
    coreRuntimeExtensions(project(":kotlinDslProviderPlugins"))
    coreRuntimeExtensions(project(":kotlinDslToolingBuilders"))

    testRuntime(project(":apiMetadata"))
}

extra["allCoreRuntimeExtensions"] = coreRuntimeExtensions.allDependencies

evaluationDependsOn(":distributions")

tasks.register<Install>("install") {
    description = "Installs the minimal distribution"
    group = "build"
    with(distributionImage("binDistImage"))
}

tasks.register<Install>("installAll") {
    description = "Installs the full distribution"
    group = "build"
    with(distributionImage("allDistImage"))
}

tasks.register<UpdateBranchStatus>("updateBranchStatus")

tasks.register<UpdateAgpVersions>("updateAgpVersions") {
    comment.set(" Generated - Update by running `./gradlew updateAgpVersions`")
    minimumSupportedMinor.set("3.4")
    propertiesFile.set(layout.projectDirectory.file("gradle/dependency-management/agp-versions.properties"))
}

fun distributionImage(named: String) =
    project(":distributions").property(named) as CopySpec

val allIncubationReports = tasks.register<IncubatingApiAggregateReportTask>("allIncubationReports") {
    val allReports = collectAllIncubationReports()
    dependsOn(allReports)
    reports = allReports.associateBy({ it.title.get() }) { it.textReportFile.asFile.get() }
}
tasks.register<Zip>("allIncubationReportsZip") {
    destinationDirectory.set(layout.buildDirectory.dir("reports/incubation"))
    archiveBaseName.set("incubating-apis")
    from(allIncubationReports.get().htmlReportFile)
    from(collectAllIncubationReports().map { it.htmlReportFile })
}

fun Project.collectAllIncubationReports() = subprojects.flatMap { it.tasks.withType(IncubatingApiReportTask::class) }

// Ensure the archives produced are reproducible
allprojects {
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        dirMode = Integer.parseInt("0755", 8)
        fileMode = Integer.parseInt("0644", 8)
    }
}

