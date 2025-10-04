Feature: POST /admin/v2/customers
  Background:
    Given the API base url is "${BASE_URL}"
    And I use bearer token from env "API_TOKEN"
    And I add header "accept" with value 'application/json'
    And I add header "accept-language" with value 'ar'
    And I add header "content-type" with value 'application/json'
    And I add header "pragma" with value 'no-cache'
    And I add header "s-source" with value 'automation'

  @positive
  Scenario Outline: Positive cases from dataset
    When I POST to "/admin/v2/customers" with json body from csv "datasets/ghhhh.csv" row <row>
    Then the response status should be from csv column "__expected_status" row <row>
    Then the response header "Content-Type" should equal "application/json"

    Examples:
      | row |
      | 1   |
      | 2   |
      | 3   |
      | 4   |
      | 5   |

  @negative
  Scenario Outline: Negative cases from dataset
    When I POST to "/admin/v2/customers" with json body from csv "datasets/ghhhh.csv" row <row>
    Then the response status should be from csv column "__expected_status" row <row>
    Then the response header "Content-Type" should equal "application/json"

    Examples:
      | row |
      | 6   |
      | 7   |
      | 8   |
      | 9   |
      | 10   |
      | 11   |
      | 12   |
      | 13   |
      | 14   |
      | 15   |

  @auth
  Scenario Outline: Auth cases from dataset
    When I POST to "/admin/v2/customers" with json body from csv "datasets/ghhhh.csv" row <row>
    Then the response status should be from csv column "__expected_status" row <row>
    Then the response header "Content-Type" should equal "application/json"

    Examples:
      | row |
      | 16   |
      | 17   |
