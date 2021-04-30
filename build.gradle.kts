val cucumberVersion: String by project

plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm") version "1.4.31"

    // Apply the java-library plugin for API and implementation separation.
    `java-library`

    // https://github.com/SpacialCircumstances/gradle-cucumber-reporting
    id("com.github.spacialcircumstances.gradle-cucumber-reporting") version "0.1.23"

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

val cucumberReportsDir: String = layout.buildDirectory.dir("cucumber-reports").get().asFile.absolutePath
val me = "cucumberTest" // alias for the "namespace" to use in the env var lookup

val cucumberTest = task<JavaExec>("cucumberTest") {
    // dependsOn assemble, testClasses // fix later, for now manually call clean & build

    // cucumber cli options
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

cucumberReports {
    outputDir = file(cucumberReportsDir)
    val reportMe = "$me.cucumberReports"
    buildId = System.getenv("$reportMe.buildId") ?: System.currentTimeMillis().toString()
    reports = files("$cucumberReportsDir/cucumber.json")
    testTasksFinalizedByReport = false
    runWithJenkins = System.getenv("$reportMe.runWithJenkins").toBoolean()
    projectNameOverride = System.getenv("$reportMe.projectNameOverride")
    //todo: enable parameterization of this path
    val trendsPath = if (System.getenv("$reportMe.trends").toBoolean()) layout.projectDirectory.dir(".gradle")
        .file("cucumberReports.trends.json").asFile else null
    trends = trendsPath
    val trendsLimitValue = System.getenv("$reportMe.trendsLimit")
    trendsLimit = trendsLimitValue?.toInt() ?: 0
    //  todo: enable setting these configurations via env vars (or other)
    //  classifications: A map with <String, String> pairs that are added to the HTML report, for example os name etc.
    //                   Use the method classification to add a single classification.
    //                   Setting this property directly will overwrite old classifications.
    //  excludeTags: A List<String> of regexes that will filter out tags so they are not present in the generated report.
    //  expandAllSteps: Set this to true to make all scenarios expanded in the generated report.
    //  notFailingStatuses: (Set<String>) Step statuses that should not be marked as failed in the report generation
    //  directorySuffix: String. Sets a suffix for directories.
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    // The new Java toolchain feature cannot be used at the project level in combination with source and/or target compatibility
    //    toolchain {
    //        languageVersion.set(JavaLanguageVersion.of(8))
    //    }
}
