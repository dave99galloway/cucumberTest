val cucumberVersion: String by project

plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm") version "1.4.31"

    // Apply the java-library plugin for API and implementation separation.
    `java-library`
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

sourceSets {
    create("cucumberTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
    sourceSets.test.configure {
        compileClasspath += sourceSets["cucumberTest"].output
        runtimeClasspath += sourceSets["cucumberTest"].output
    }
}

val cucumberTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.implementation.get())
}

val cucumberTestApi: Configuration by configurations.getting {
    extendsFrom(configurations.api.get())
}
//https://docs.gradle.org/current/userguide/java_testing.html#sec:configuring_java_integration_tests - suggests using runtimeOnly
configurations["cucumberTestApi"].extendsFrom(configurations.testApi.get())


dependencies {
    // Align versions of all Kotlin components
    // all dependencies given maximum visibility across all source sets
    // (since they all inherit directly or indirectly from main)
    // watch this come back to bite us...
    api(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Use the Kotlin JDK 8 standard library.
    api("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    api("com.google.guava:guava:30.0-jre")

    // Use the Kotlin test library.
    api("org.jetbrains.kotlin:kotlin-test")

    // Use the Kotlin JUnit integration.
    api("org.jetbrains.kotlin:kotlin-test-junit")

    //cucumber specific dependencies - still exposed by api scope
    api(group = "io.cucumber", name = "cucumber-java8", version = cucumberVersion)
    api(group = "io.cucumber", name = "cucumber-java", version = cucumberVersion)
    api(group = "io.cucumber", name = "cucumber-junit", version = cucumberVersion)
    // if guice is on the classpath , it must be configured. leave for now
    // api(group = "io.cucumber", name = "cucumber-guice", version = cucumberVersion)
}

tasks.named<Wrapper>("wrapper") {
    gradleVersion = "7.0"
    distributionType = Wrapper.DistributionType.ALL
}

val cucumberTest = task<JavaExec>("cucumberTest") {
    // dependsOn assemble, testClasses // fix later, for now manually call clean & build

    // cucumber cli options
    val cucumberReportsDir = layout.buildDirectory.dir("cucumber-reports").get().asFile.absolutePath
    val me = "cucumberTest" // alias for the "namespace" to use in the env var lookup
    // for this to work with the IDEA run/debug config an the EnvFile plugin, the "experimental integrations" checkbox must be set
    // if this breaks (as the warning on the checkbox implies it might do), then revert to using Dotenv as per the previous commit
    val tags: String = System.getenv("$me.tags") ?: "not @Ignore"
    println("tags = $tags")


    //core javaexec options
    description = "Runs task cucumber tests."
    group = "verification"

    main = "io.cucumber.core.cli.Main"
    classpath = sourceSets["cucumberTest"].runtimeClasspath.plus(sourceSets.main.get().output)
    //.plus(sourceSets.test.get().output) // shouldn't use test src output as we might use test to test the cucumberTest classes
    args = listOf(
        "--tags", tags,
        "--plugin", "pretty",
        "--plugin", "html:$cucumberReportsDir/cucumber-html-report.html",
        "--plugin", "json:$cucumberReportsDir/cucumber.json",
        // "--plugin", "progress" // can't use at the same time as 'pretty' as both use stdout and it doesn't make sense
        // to redirect either to a file
    )
    shouldRunAfter("test")
}

// tasks.check { dependsOn(integrationTest) }

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    // The new Java toolchain feature cannot be used at the project level in combination with source and/or target compatibility
    //    toolchain {
    //        languageVersion.set(JavaLanguageVersion.of(8))
    //    }
}

