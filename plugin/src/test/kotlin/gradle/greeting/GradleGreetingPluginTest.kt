/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package gradle.greeting

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * A simple unit test for the 'gradle.greeting.greeting' plugin.
 */
class GradleGreetingPluginTest {
    @Test
    fun `plugin registers task`() {
        // Create a test project and apply the plugin
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("gradle.greeting.greeting")

        // Verify the result
        assertNotNull(project.tasks.findByName("greeting"))
    }
}