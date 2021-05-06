package com.github.dave99galloway.cucumbertest.glue

import io.cucumber.datatable.DataTable
import io.cucumber.java8.En

class StepDefinitionArgumentHandlingSteps : En {


    init {
        Given("I want to send some arbitrary text to my step definitions") { _: String ->

        }
        Given("I want to use a data table in my then step") {

        }
        When("I run the test") {

        }
        Then("the data table is passed to the step definition") { _: DataTable ->

        }
        When("I do {string}") { _: String ->

        }
        Then("I see {string}") { _: String ->

        }
        Given("some preconditions that are going to get executed a few times") {

        }
    }


}