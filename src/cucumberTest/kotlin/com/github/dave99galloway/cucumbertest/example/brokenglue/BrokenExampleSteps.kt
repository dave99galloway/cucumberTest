package com.github.dave99galloway.cucumbertest.example.brokenglue

import io.cucumber.java.Before
import io.cucumber.java.PendingException
import io.cucumber.java.Scenario
import io.cucumber.java8.En
import java.io.File

class BrokenExampleSteps : En {

    private lateinit var stepScenario: Scenario

    @Before
    fun before(scenario: Scenario) {
        stepScenario = scenario
    }

    init {
        Given("some preconditions") {
            stepScenario.log("something's about to happen")
        }

        When("I take some action") {
            stepScenario.attach(
                File("src/cucumberTest/resources/cucumber.icon.png").readBytes(),
                "image/png",
                "fake screenshot"
            )
        }

        Then("some result happens") {
            throw PendingException()
        }
    }
}