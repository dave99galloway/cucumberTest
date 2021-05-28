@file:Suppress("PackageDirectoryMismatch")

package com.github.dave99galloway.gradle.cucumbertest


import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.InitialValueExtraPropertyDelegateProvider
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.task
import java.io.File


/**
 * Provides the existing [main][org.gradle.api.tasks.SourceSet] element.
 */
val org.gradle.api.tasks.SourceSetContainer.main: NamedDomainObjectProvider<org.gradle.api.tasks.SourceSet>
    get() = named<org.gradle.api.tasks.SourceSet>("main")

/**
 * Retrieves the [sourceSets][org.gradle.api.tasks.SourceSetContainer] extension.
 */
val org.gradle.api.Project.sourceSets: org.gradle.api.tasks.SourceSetContainer
    get() =
        (this as org.gradle.api.plugins.ExtensionAware).extensions.getByName("sourceSets") as org.gradle.api.tasks.SourceSetContainer

/**
 * Configures the [sourceSets][org.gradle.api.tasks.SourceSetContainer] extension.
 */
fun org.gradle.api.Project.sourceSets(configure: Action<org.gradle.api.tasks.SourceSetContainer>): Unit =
    (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("sourceSets", configure)

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

