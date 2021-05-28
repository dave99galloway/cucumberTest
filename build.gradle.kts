val cucumberVersion: String by project
val slf4jVersion: String by project

plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm") version "1.4.31"

    // Apply the java-library plugin for API and implementation separation.
    `java-library`

    // https://github.com/SpacialCircumstances/gradle-cucumber-reporting
    id("com.github.spacialcircumstances.gradle-cucumber-reporting") version "0.1.23"

    id("gradle.greeting.greeting")


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


interface CucumberTestPluginExtension {
    val cucumberTestEnvVarNamespace: Property<String>  // alias for the "namespace" to use in the env var lookup

    /**
     *  path to the reports output dir. defaults to the build dir/cucumberReportsFolderName like this:
     *  layout.buildDirectory.dir(cucumberTestEnvVarNamespace.cucumberReportsFolderName()).get().asFile
     *  (BROKEN) but you can override this with a java.io.File here in the configuration block,(BROKEN)
     *  or with an absolute path in the env vars. If you provide a relative path in the env vars, it's resolved against
     *  the current working directory
     */
    val cucumberReportsDir: Property<File> //

    /**
     * a cucumber expression such as "@DataTable or @DocString". Value set by the plugin configuration takes precedence,
     * followed by the env var value of $cucumberTestEnvVarNamespace.tags, and defaulting to "not @Ignore" if nothing else is set
     */
    val tags: Property<String>

    /**
     * optional path to search for features. If none, then the whole classpath is searched for features
     */
    val features: Property<String?>

    /**
     * comma separated list of packages to search for step defs in. If None, the whole classpath is searched
     */
    val glue: Property<String?>

    /**
     * comma separated list of plugins to add. The core options of "json:$cucumberReportsDir/cucumber.json"
     * and "pretty" are used regardless of whether any additional plugins are specified here.
     * DO NOT add the "--plugin" prefix args here, the cucumberTest task will do this
     */
    val plugins: Property<String?>

    val options: Property<String?>

}

class CucumberTestPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // the extension must be created here or else it can't be used to configure the plugin later
        val extension = project.extensions.create<CucumberTestPluginExtension>("cucumberTestConfig")
        //to be able to customise these values via the CucumberTestPluginExtension or via the config block for cucumberTest itself
        // we're going to need to create that specialised sub class of JavaExec after all. For now - accept we can't set this via plugin config
        // these properties need to be set here in the config stage as extra props so that the cucumberReports extension can access them later
        val cucumberTestEnvVarNamespace by project.cucumberTestEnvVarNamespace(extension)
        val cucumberReportsDir: String by project.cucumberReportsDir(extension, cucumberTestEnvVarNamespace)
        println("cucumberReportsFolderName: ${cucumberTestEnvVarNamespace.cucumberReportsFolderName()}")
        println("cucumberReportsDir:- $cucumberReportsDir")

        /**
         * create a task called "cucumberTest" of type JavaExec and configure the task
         * JavaExec does a single invocation of a child java process using the configuration in the block below.
         * If we wanted to split the task into multiple invocations, e.g. per feature, then we could extend the JavaExec
         * task and override it's exec() method (the one annotated with @TaskAction)
         */
        project.task<JavaExec>("cucumberTest") {
            // todo: make the task name configurable


            // core gradle task options
            description = "Runs task cucumber tests."
            group = "verification"

            //core javaexec options
            main = "io.cucumber.core.cli.Main"


            doFirst {

                // System.getenv().forEach { (t, u) -> println("key: $t, value: $u") }

                val argsList = mutableListOf<String>()

                // cucumber cli options
                // for this to work with the IDEA run/debug config an the EnvFile plugin, the "experimental integrations" checkbox must be set
                // if this breaks (as the warning on the checkbox implies it might do), then revert to using Dotenv as per the previous commit
                argsList.addAll(cucumberTestEnvVarNamespace.getTagsList(extension))

                cucumberTestEnvVarNamespace.getGlueList(extension)?.let { glueList -> argsList.addAll(glueList) }

                argsList.addAll(cucumberTestEnvVarNamespace.getPluginsList(cucumberReportsDir, extension))

                cucumberTestEnvVarNamespace.getOptionsList(extension)
                    ?.let { optionalArgs -> argsList.addAll(optionalArgs) }

                cucumberTestEnvVarNamespace.getFeaturesPath(extension)?.let { featurePath -> argsList.add(featurePath) }

                //core javaexec options
                classpath =
                    project.sourceSets["cucumberTest"].runtimeClasspath.plus(project.sourceSets.main.get().output)
                //.plus(sourceSets.test.get().output) // shouldn't use test src output as we might use test to test the cucumberTest classes

                argsList.forEach { println(it) }

                args = argsList.toList()

            }
        }
    }

    fun Project.cucumberReportsDir(
        extension: CucumberTestPluginExtension,
        cucumberTestEnvVarNamespace: String
    ): InitialValueExtraPropertyDelegateProvider<String> {
        val reportDir: File = extension.cucumberReportsDir.getOrElse(
            System.getenv("$cucumberTestEnvVarNamespace.cucumberReportsDir")?.let { pathname -> File(pathname) }
                ?: layout.buildDirectory.dir(cucumberTestEnvVarNamespace.cucumberReportsFolderName()).get().asFile
        )
        return this.extra(reportDir.absolutePath)
    }

    fun Project.cucumberTestEnvVarNamespace(
        extension: CucumberTestPluginExtension
    ): InitialValueExtraPropertyDelegateProvider<String> {
        return this.extra(extension.cucumberTestEnvVarNamespace.getOrElse("cucumberTest"))
    }

    /**
     * get the folder name to use to create the reports directory. This is ignored if cucumberReportsDir has also been
     * set either as an env var or directly in the configuration of this task in your project's build file
     */
    private fun String.cucumberReportsFolderName() =
        System.getenv("${this}.cucumberReportsFolderName") ?: "cucumber-reports"


    fun String.getGlueList(extension: CucumberTestPluginExtension): List<String>? {
        return (extension.glue.orNull ?: System.getenv("${this}.glue"))?.split(",")
            ?.map { glueArg -> listOf("--glue", glueArg) }?.flatten()
    }

    fun String.getPluginsList(cucumberReportsDir: String, extension: CucumberTestPluginExtension): List<String> {
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
            (extension.plugins.orNull ?: System.getenv("${this}.plugins"))?.split(",")
                ?.map { glueArg -> listOf("--plugin", glueArg) }
                ?.flatten()
        optionalPlugins?.let { plugins -> pluginsList.addAll(plugins) }
        return pluginsList
    }

    /**
     * scan the extension and then the env var for tags. if none are supplied assume we don't want @Ignore tags to run
     * @return List<String>
     */
    fun String.getTagsList(
        extension: CucumberTestPluginExtension
    ): List<String> {
        val tags: String = extension.tags.getOrElse(System.getenv("${this}.tags") ?: "not @Ignore")
        return listOf("--tags", tags)
    }

    /**
     * parse the env var to get the path to features
     * e.g. cucumberTest.features="classpath:features/sub-features"
     * fully qualified file system paths will also work but will probably be a pain in actual usage
     * @return String? If no path is supplied, then null is returned, and no arg is passed for features,
     * so the entire classpath will be scanned for features
     */
    fun String.getFeaturesPath(extension: CucumberTestPluginExtension): String? {
        return extension.features.orNull ?: System.getenv("${this}.features")
    }


    /**
     * parse the env var to get additional miscellaneous options. these are comma separated.
     * to do a dry run in monochrome mode add this to the env var / file
     * cucumberTest.options="-m,--dry-run"
     * full list of options is at https://github.com/cucumber/cucumber-jvm/blob/main/core/src/main/resources/io/cucumber/core/options/USAGE.txt
     * @return List<String>?
     */
    fun String.getOptionsList(extension: CucumberTestPluginExtension): List<String>? {
        return (extension.options.orNull ?: System.getenv("${this}.options"))?.split(",")
    }
}

apply<CucumberTestPlugin>()

configure<CucumberTestPluginExtension> {
    // this doesn't currently work as it happens too late. might work when cucumberReports is added as a task in the same plugin
    // cucumberReportsDir.set(layout.projectDirectory.dir(".gradle").asFile)
    // tags.set("@DataTable or @DocString")
    // either of these will work
    // features.set("/Users/dave/git/dave99galloway/cucumberTest/src/cucumberTest/resources/features/feature2.feature")
    // features.set("classpath:features/feature2.feature")
    // this works fine
    // glue.set("com.github.dave99galloway.cucumbertest.example.brokenglue")
    // plugins.set("html:build/cucumber-reports/cucumber-html-report.html")
    // plugins.set("com.github.dave99galloway.cucumbertest.plugins.ScenarioStepListener")
    // options.set("-m,--dry-run")
    // options.set("-m")
}

// configuration at this level isn't really interesting unless we customise the task
//tasks.named<JavaExec>("cucumberTest") {
//    this.args?.plusAssign("hi")
//
//
//}


cucumberReports {
    // get properties set by the CucumberTestPlugin.cucumberTest task in the project extra properties
    // there's probably a better, more typesafe way of doing this,
    // and we need to configure fallbacks in case these aren't set by CucumberTestPlugin.cucumberTest
    val cucumberReportsDir: String = project.extra.get("cucumberReportsDir") as String
    val reportMe = "${project.extra.get("cucumberTestEnvVarNamespace")}.cucumberReports"

    outputDir = file(cucumberReportsDir)
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
