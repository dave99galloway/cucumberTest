val cucumberVersion: String by project
val slf4jVersion: String by project

plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm") version "1.4.31"

    // Apply the java-library plugin for API and implementation separation.
    `java-library`

//    // https://github.com/SpacialCircumstances/gradle-cucumber-reporting
//    id("com.github.spacialcircumstances.gradle-cucumber-reporting") version "0.1.23"

    //cucumber-test-plugin
    `cucumber-test-plugin`


}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

//sourceSets {
//    create("cucumberTest") {
//        compileClasspath += sourceSets.main.get().output
//        runtimeClasspath += sourceSets.main.get().output
//    }
//    sourceSets.test.configure {
//        compileClasspath += sourceSets["cucumberTest"].output
//        runtimeClasspath += sourceSets["cucumberTest"].output
//    }
//}

//val cucumberTestImplementation: Configuration by configurations.getting {
//    extendsFrom(configurations.implementation.get())
//}
//
//val cucumberTestApi: Configuration by configurations.getting {
//    extendsFrom(configurations.api.get())
//}
////https://docs.gradle.org/current/userguide/java_testing.html#sec:configuring_java_integration_tests - suggests using runtimeOnly
//configurations["cucumberTestApi"].extendsFrom(configurations.testApi.get())


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

    // according to http://slf4j.org/manual.html#libraries only the slf4j-api dependency should be declared transitively
    // in libraries such as this. a goal of this framework is to be "batteries included" so we might well end up
    // deliberately violating this rule
    api(group = "org.slf4j", name = "slf4j-api", version = slf4jVersion)
    // use the simplest logging solution for now, although we might want to look at changing this so we can add dynamic
    // FileHandlers for reporting Scenario results to 3rd party tools in bulk
    api(group = "org.slf4j", name = "jul-to-slf4j", version = slf4jVersion)
    api(group = "org.slf4j", name = "slf4j-simple", version = slf4jVersion)
    api("org.jetbrains.kotlin:kotlin-reflect")
}

tasks.named<Wrapper>("wrapper") {
    gradleVersion = "7.0"
    distributionType = Wrapper.DistributionType.ALL
}


//
//apply<CucumberTestPlugin>()
//
//configure<CucumberTestPluginExtension> {
//    // this doesn't currently work as it happens too late. might work when cucumberReports is added as a task in the same plugin
//    // cucumberReportsDir.set(layout.projectDirectory.dir(".gradle").asFile)
//    // tags.set("@DataTable or @DocString")
//    // either of these will work
//    // features.set("/Users/dave/git/dave99galloway/cucumberTest/src/cucumberTest/resources/features/feature2.feature")
//    // features.set("classpath:features/feature2.feature")
//    // this works fine
//    // glue.set("com.github.dave99galloway.cucumbertest.example.brokenglue")
//    // plugins.set("html:build/cucumber-reports/cucumber-html-report.html")
//    // plugins.set("com.github.dave99galloway.cucumbertest.plugins.ScenarioStepListener")
//    // options.set("-m,--dry-run")
//    // options.set("-m")
//}

// configuration at this level isn't really interesting unless we customise the task
//tasks.named<JavaExec>("cucumberTest") {
//    this.args?.plusAssign("hi")
//
//
//}

//
//cucumberReports {
//    // get properties set by the CucumberTestPlugin.cucumberTest task in the project extra properties
//    // there's probably a better, more typesafe way of doing this,
//    // and we need to configure fallbacks in case these aren't set by CucumberTestPlugin.cucumberTest
//    val cucumberReportsDir: String = project.extra.get("cucumberReportsDir") as String
//    val reportMe = "${project.extra.get("cucumberTestEnvVarNamespace")}.cucumberReports"
//
//    outputDir = file(cucumberReportsDir)
//    buildId = System.getenv("$reportMe.buildId") ?: System.currentTimeMillis().toString()
//    reports = files("$cucumberReportsDir/cucumber.json")
//    testTasksFinalizedByReport = false
//    runWithJenkins = System.getenv("$reportMe.runWithJenkins").toBoolean()
//    projectNameOverride = System.getenv("$reportMe.projectNameOverride")
//    //todo: enable parameterization of this path
//    val trendsPath = if (System.getenv("$reportMe.trends").toBoolean()) layout.projectDirectory.dir(".gradle")
//        .file("cucumberReports.trends.json").asFile else null
//    trends = trendsPath
//    val trendsLimitValue = System.getenv("$reportMe.trendsLimit")
//    trendsLimit = trendsLimitValue?.toInt() ?: 0
//    //  todo: enable setting these configurations via env vars (or other)
//    //  classifications: A map with <String, String> pairs that are added to the HTML report, for example os name etc.
//    //                   Use the method classification to add a single classification.
//    //                   Setting this property directly will overwrite old classifications.
//    //  excludeTags: A List<String> of regexes that will filter out tags so they are not present in the generated report.
//    //  expandAllSteps: Set this to true to make all scenarios expanded in the generated report.
//    //  notFailingStatuses: (Set<String>) Step statuses that should not be marked as failed in the report generation
//    //  directorySuffix: String. Sets a suffix for directories.
//}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    // The new Java toolchain feature cannot be used at the project level in combination with source and/or target compatibility
    //    toolchain {
    //        languageVersion.set(JavaLanguageVersion.of(8))
    //    }
}
