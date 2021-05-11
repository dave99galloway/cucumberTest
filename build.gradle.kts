val cucumberVersion: String by project
val slf4jVersion: String by project

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


// tasks.check { dependsOn(integrationTest) }

interface CucumberTestPluginExtension {
    val me: Property<String>  // alias for the "namespace" to use in the env var lookup

}

class CucumberTestPlugin : Plugin<Project> {
//


    override fun apply(project: Project) {
        val extension = project.extensions.create<CucumberTestPluginExtension>("cucumberTestConfig")

        val me = extension.me.getOrElse("cucumberTest")

        val cucumberReportsDir: String =
            project.layout.buildDirectory.dir("cucumber-reports").get().asFile.absolutePath



        project.task<JavaExec>("cucumberTest") {

            // dependsOn assemble, testClasses // fix later, for now manually call clean & build

            val argsList = mutableListOf<String>()

            // cucumber cli options
            // for this to work with the IDEA run/debug config an the EnvFile plugin, the "experimental integrations" checkbox must be set
            // if this breaks (as the warning on the checkbox implies it might do), then revert to using Dotenv as per the previous commit
            argsList.addAll(getTagsList(me))

            getGlueList(me)?.let { glueList -> argsList.addAll(glueList) }

            argsList.addAll(getPluginsList(me, cucumberReportsDir))

            getOptionsList(me)?.let { optionalArgs -> argsList.addAll(optionalArgs) }

            getFeaturesPath(me)?.let { featurePath -> argsList.add(featurePath) }

            // core gradle task options
            description = "Runs task cucumber tests."
            group = "verification"


            //core javaexec options
            main = "io.cucumber.core.cli.Main"
            classpath = project.sourceSets["cucumberTest"].runtimeClasspath.plus(project.sourceSets.main.get().output)
            //.plus(sourceSets.test.get().output) // shouldn't use test src output as we might use test to test the cucumberTest classes
            argsList.forEach { println(it) }
            args = argsList.toList()
            //shouldRunAfter("test")
        }
    }


    fun getGlueList(me: String): List<String>? {
        return System.getenv("$me.glue")?.split(",")?.map { glueArg -> listOf("--glue", glueArg) }?.flatten()
    }

    fun getPluginsList(me: String, cucumberReportsDir: String): List<String> {
        val pluginsList = mutableListOf<String>()
        val corePlugins = listOf(
            "--plugin", "json:$cucumberReportsDir/cucumber.json",
            "--plugin", "pretty",
        )
        pluginsList.addAll(corePlugins)
        // to add the scenario Step listener and html reports add this to the environment variables / .env file
        /*
            cucumberTest.plugins="com.github.dave99galloway.cucumbertest.plugins.ScenarioStepListener,html:build/cucumber-reports/cucumber-html-report.html"
         */
        // note that you need to know that the output dir is build/cucumber-reports
        val optionalPlugins =
            System.getenv("$me.plugins")?.split(",")?.map { glueArg -> listOf("--plugin", glueArg) }?.flatten()
        optionalPlugins?.let { plugins -> pluginsList.addAll(plugins) }
        return pluginsList
    }

    /**
     * scan the env var for tags. if none are supplied assume we don't want @Ignore tags to run
     * @return List<String>
     */
    fun getTagsList(me: String): List<String> {
        return listOf("--tags", System.getenv("$me.tags") ?: "not @Ignore")
    }

    /**
     * parse the env var to get the path to features
     * e.g. cucumberTest.features="classpath:features/sub-features"
     * fully qualified file system paths will also work but will probably be a pain in actual usage
     * @return String? If no path is supplied, then null is returned, and no arg is passed for features,
     * so the entire classpath will be scanned for features
     */
    fun getFeaturesPath(me: String): String? {
        return System.getenv("$me.features")
    }


    /**
     * parse the env var to get additional miscellaneous options. these are comma separated.
     * to do a dry run in monochrome mode add this to the env var / file
     * cucumberTest.options="-m,--dry-run"
     * full list of options is at https://github.com/cucumber/cucumber-jvm/blob/main/core/src/main/resources/io/cucumber/core/options/USAGE.txt
     * @return List<String>?
     */
    fun getOptionsList(me: String): List<String>? {
        return System.getenv("$me.options")?.split(",")
    }
}

apply<CucumberTestPlugin>()

interface GreetingPluginExtension {
    val message: Property<String>
    val greeter: Property<String>
}

class GreetingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.task("hello") {
            val argsList = mutableListOf<String>()

            val extension = project.extensions.create<GreetingPluginExtension>("greeting")


            // core gradle task options
            description = "says hello."
            group = "verification"
            val message = extension.message.getOrElse("Howdy")
            doLast {
                println("Hello from the GreetingPlugin")
                println("$message from ${extension.greeter.get()}")

            }
        }

        project.task("goodbye") {
            doLast {
                println("hasta la vista from the GreetingPlugin")
            }
        }
    }

    /**
     * scan the env var for tags. if none are supplied assume we don't want @Ignore tags to run
     * @return List<String>
     */
    fun getTagsList(): List<String> {
        return listOf("--tags", System.getenv(".tags") ?: "not @Ignore")
    }
}

// Apply the plugin
apply<GreetingPlugin>()

// Configure the extension using a DSL block
configure<GreetingPluginExtension> {
    // message.set("Hi")
    greeter.set("Gradle")
}


//
//cucumberReports {
//    outputDir = file(cucumberReportsDir)
//    val reportMe = "$me.cucumberReports"
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
