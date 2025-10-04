Feature: POST /admin/v2/customers
  Background:
    Given the API base url is "${BASE_URL}"
    And I use bearer token from env "API_TOKEN"
    And I add header "accept" with value 'application/json'
    And I add header "accept-language" with value 'ar'
    And I add header "content-type" with value 'application/json'
    And I add header "pragma" with value 'no-cache'
    And I add header "s-source" with value 'automation'

  @happy
  Scenario: Happy path generated from cURL
    When I POST to "/admin/v2/customers" with json body:
      """
{
    "first_name": "moe",
    "last_name": " daabesdaabesdas",
    "email": "m.daabes+93253@salla.sa",
    "birthday": "08-09-2015",
    "gender": "male",
    "mobile_code_country":"962",
    "mobile":"799798444"
    
    
}
      """
    Then the response status should be 200
    Then the response header "Content-Type" should equal "application/json"

  @auth
  Scenario: Missing token returns 401
    Given I remove authorization header
    When I POST to "/admin/v2/customers" with json body:
      """
{
    "first_name": "moe",
    "last_name": " daabesdaabesdas",
    "email": "m.daabes+93253@salla.sa",
    "birthday": "08-09-2015",
    "gender": "male",
    "mobile_code_country":"962",
    "mobile":"799798444"
    
    
}
      """
    Then the response status should be 401
    Then the response header "Content-Type" should equal "application/json"

  @negative
  Scenario: missing required field 'first_name'
    When I POST to "/admin/v2/customers" with json body:
      """
{
  "last_name" : " daabesdaabesdas",
  "email" : "m.daabes+93253@salla.sa",
  "birthday" : "08-09-2015",
  "gender" : "male",
  "mobile_code_country" : "962",
  "mobile" : "799798444"
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative
  Scenario: missing required field 'last_name'
    When I POST to "/admin/v2/customers" with json body:
      """
{
  "first_name" : "moe",
  "email" : "m.daabes+93253@salla.sa",
  "birthday" : "08-09-2015",
  "gender" : "male",
  "mobile_code_country" : "962",
  "mobile" : "799798444"
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative
  Scenario: missing required field 'email'
    When I POST to "/admin/v2/customers" with json body:
      """
{
  "first_name" : "moe",
  "last_name" : " daabesdaabesdas",
  "birthday" : "08-09-2015",
  "gender" : "male",
  "mobile_code_country" : "962",
  "mobile" : "799798444"
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative
  Scenario: missing required field 'birthday'
    When I POST to "/admin/v2/customers" with json body:
      """
{
  "first_name" : "moe",
  "last_name" : " daabesdaabesdas",
  "email" : "m.daabes+93253@salla.sa",
  "gender" : "male",
  "mobile_code_country" : "962",
  "mobile" : "799798444"
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative
  Scenario: missing required field 'gender'
    When I POST to "/admin/v2/customers" with json body:
      """
{
  "first_name" : "moe",
  "last_name" : " daabesdaabesdas",
  "email" : "m.daabes+93253@salla.sa",
  "birthday" : "08-09-2015",
  "mobile_code_country" : "962",
  "mobile" : "799798444"
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative
  Scenario: missing required field 'mobile_code_country'
    When I POST to "/admin/v2/customers" with json body:
      """
{
  "first_name" : "moe",
  "last_name" : " daabesdaabesdas",
  "email" : "m.daabes+93253@salla.sa",
  "birthday" : "08-09-2015",
  "gender" : "male",
  "mobile" : "799798444"
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative
  Scenario: missing required field 'mobile'
    When I POST to "/admin/v2/customers" with json body:
      """
{
  "first_name" : "moe",
  "last_name" : " daabesdaabesdas",
  "email" : "m.daabes+93253@salla.sa",
  "birthday" : "08-09-2015",
  "gender" : "male",
  "mobile_code_country" : "962"
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"
