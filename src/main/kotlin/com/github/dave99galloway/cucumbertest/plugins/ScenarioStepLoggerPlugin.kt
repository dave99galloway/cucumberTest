package com.github.dave99galloway.cucumbertest.plugins

import com.github.dave99galloway.cucumbertest.logging.logger
import com.github.dave99galloway.cucumbertest.logging.resetLoggers
import io.cucumber.core.gherkin.DataTableArgument
import io.cucumber.core.gherkin.DocStringArgument
import io.cucumber.datatable.DataTable
import io.cucumber.plugin.EventListener
import io.cucumber.plugin.event.EventPublisher
import io.cucumber.plugin.event.HookTestStep
import io.cucumber.plugin.event.PickleStepTestStep
import io.cucumber.plugin.event.TestCaseFinished
import io.cucumber.plugin.event.TestCaseStarted
import io.cucumber.plugin.event.TestRunFinished
import io.cucumber.plugin.event.TestRunStarted
import io.cucumber.plugin.event.TestStepFinished
import io.cucumber.plugin.event.TestStepStarted
import java.io.File
import java.net.URL

class ScenarioStepListener(private val handler: ScenarioStepHandler = ScenarioStepHandler()) : EventListener {
    init {
        resetLoggers()
    }

    override fun setEventPublisher(publisher: EventPublisher) {
        publisher.registerHandlerFor(TestCaseStarted::class.java, handler::testCaseStarted)
        publisher.registerHandlerFor(TestCaseFinished::class.java, handler::testCaseFinished)
        publisher.registerHandlerFor(TestRunStarted::class.java, handler::testRunStarted)
        publisher.registerHandlerFor(TestRunFinished::class.java, handler::testRunFinished)
        publisher.registerHandlerFor(TestStepStarted::class.java, handler::testStepStarted)
        publisher.registerHandlerFor(TestStepFinished::class.java, handler::testStepFinished)
    }
}

class ScenarioStepHandler {

    fun testRunStarted(event: TestRunStarted) {
        log.info("Test run started at ${event.instant}")
    }

    fun testRunFinished(event: TestRunFinished) {
        log.info("Test run finished at ${event.instant} ${event.result}")
    }

    fun testCaseStarted(event: TestCaseStarted) {
        val testCase = event.testCase
        log.info("Begin ${testCase.keyword}: ${testCase.name}")
        log.debug("${testCase.keyword}: ${testCase.name} started at ${event.instant}")
        val uriString: String = if (testCase.uri.toString().startsWith(CLASSPATH)) {
            val featureUrl: URL? =
                this.javaClass.classLoader.getResource(testCase.uri.toString().replace(CLASSPATH, ""))
            File(featureUrl?.path ?: testCase.uri.toString()).absolutePath
        } else {
            testCase.uri.toString()
        }
        log.debug("'${testCase.keyword}: ${testCase.name}' running from $uriString:${testCase.location.line}:${testCase.location.column}")
    }

    fun testCaseFinished(event: TestCaseFinished) {
        val testCase = event.testCase
        log.info("Finished ${testCase.keyword}: ${testCase.name} ${event.result}")
        log.debug("${testCase.keyword}: ${testCase.name} finished at ${event.instant}")
        val uriString: String = if (testCase.uri.toString().startsWith(CLASSPATH)) {
            val featureUrl: URL? =
                this.javaClass.classLoader.getResource(testCase.uri.toString().replace(CLASSPATH, ""))
            File(featureUrl?.path ?: testCase.uri.toString()).absolutePath
        } else {
            testCase.uri.toString()
        }
        log.debug("'${testCase.keyword}: ${testCase.name}' finished running from $uriString:${testCase.location.line}:${testCase.location.column}")
    }

    fun testStepStarted(event: TestStepStarted) {
        when (val step = event.testStep) {
            is PickleStepTestStep -> {
                val stepArgumentAsText = stepArgumentAsText(step)
                log.info("Started Step: ${step.step.keyword}${step.step.text}${stepArgumentAsText?.let { argText -> "$LINE_SEP$argText" } ?: ""}")
            }
            is HookTestStep -> log.trace("Ignoring step. ${step.hookType}")
            else -> log.warn("unknown step type for $step: ${step::class.java}")
        }
    }

    fun testStepFinished(event: TestStepFinished) {
        when (val step = event.testStep) {
            is PickleStepTestStep -> {
                log.info("Finished Step: ${step.step.keyword}${step.step.text} ${event.result}")
            }
            is HookTestStep -> log.trace("Ignoring step. ${step.hookType}")
            else -> log.warn("unknown step type for $step: ${step::class.java}")
        }
    }

    companion object {
        private val log by logger()

        fun stepArgumentAsText(step: PickleStepTestStep): String? {
            return when (val argument = step.step.argument) {
                is Nothing? -> return null
                is DocStringArgument -> argument.content
                is DataTableArgument -> DataTable.create(argument.cells()).toString()
                else -> return null
            }
        }

        private const val CLASSPATH = "classpath:"
    }
}

val LINE_SEP: String = System.getProperty("line.separator")
