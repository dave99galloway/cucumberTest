Feature: scenarios and examples with arguments
  As a tester
  I want cucumber pass data to step definitions
  So that I can data drive my tests

  @DataTable
  Scenario: Data tables are passed as DataTable arguments
    Given I want to use a data table in my then step
    When I run the test
    Then the data table is passed to the step definition
      | col1 | col2 | col3 |
      | r1c1 | r1c2 | r1c3 |
      | r2c1 | r2c2 | r2c3 |
      | r3c1 | r3c2 | r3c3 |


  @Trickier
  Scenario Outline: Scenario Outline data is passed as arguments to step definitions
    Given some preconditions that are going to get executed a few times
    When I do "<action>"
    Then I see "<result>"

    @one
    Examples: one
      | action | result |
      | foo    | bar    |

    @two @three
    Examples: two three
      | action | result |
      | foo2   | bar3   |
      | foo3   | bar3   |

  @DocString
  Scenario: Scenarios with Docstrings can pass arbitrary text
    Given I want to send some arbitrary text to my step definitions
"""
{
  "text": "Insert humorous data here"
}
"""