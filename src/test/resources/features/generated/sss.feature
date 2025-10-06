Feature: PUT /admin/v2/customers/792145313
  Background:
    Given the API base url is "${BASE_URL}"
    And I use bearer token from env "API_TOKEN"
    And I add header "accept" with value 'application/json'
    And I add header "accept-language" with value 'ar'
    And I add header "cache-control" with value 'no-cache'
    And I add header "content-type" with value 'application/json'
    And I add header "CF-Access-Client-Secret" with value '8f66873180160f05755c4b4484b6247998be0bf5281ef5eb7941c9b2a7dd4f63'
    And I add header "CF-Access-Client-Id" with value 'b7a7ec459bf70998c7df80fe8e74ee36.access'

  @happy
  Scenario: Happy path generated from cURL
    When I PUT to "/admin/v2/customers/792145313" with json body:
      """
{
    "first_name": "moe",
    "last_name": "daanes",
    "email": "daabes3333@gmail.com",
    "birthday": "30-09-2015",
    "gender": "male",
    "fields": {
        "1206348510": "dddd",
        "1780108253": ""
    }
}
      """
    Then the response status should be 200
    Then the response header "Content-Type" should equal "application/json"

  @auth
  Scenario: Missing token returns 401
    Given I remove authorization header
    When I PUT to "/admin/v2/customers/792145313" with json body:
      """
{
    "first_name": "moe",
    "last_name": "daanes",
    "email": "daabes3333@gmail.com",
    "birthday": "30-09-2015",
    "gender": "male",
    "fields": {
        "1206348510": "dddd",
        "1780108253": ""
    }
}
      """
    Then the response status should be 401
    Then the response header "Content-Type" should equal "application/json"

  @negative
  Scenario: missing required field 'first_name'
    When I PUT to "/admin/v2/customers/792145313" with json body:
      """
{
  "last_name" : "daanes",
  "email" : "daabes3333@gmail.com",
  "birthday" : "30-09-2015",
  "gender" : "male",
  "fields" : {
    "1206348510" : "dddd",
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative
  Scenario: missing required field 'last_name'
    When I PUT to "/admin/v2/customers/792145313" with json body:
      """
{
  "first_name" : "moe",
  "email" : "daabes3333@gmail.com",
  "birthday" : "30-09-2015",
  "gender" : "male",
  "fields" : {
    "1206348510" : "dddd",
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative
  Scenario: missing required field 'email'
    When I PUT to "/admin/v2/customers/792145313" with json body:
      """
{
  "first_name" : "moe",
  "last_name" : "daanes",
  "birthday" : "30-09-2015",
  "gender" : "male",
  "fields" : {
    "1206348510" : "dddd",
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative
  Scenario: missing required field 'birthday'
    When I PUT to "/admin/v2/customers/792145313" with json body:
      """
{
  "first_name" : "moe",
  "last_name" : "daanes",
  "email" : "daabes3333@gmail.com",
  "gender" : "male",
  "fields" : {
    "1206348510" : "dddd",
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative
  Scenario: missing required field 'gender'
    When I PUT to "/admin/v2/customers/792145313" with json body:
      """
{
  "first_name" : "moe",
  "last_name" : "daanes",
  "email" : "daabes3333@gmail.com",
  "birthday" : "30-09-2015",
  "fields" : {
    "1206348510" : "dddd",
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative
  Scenario: missing required field 'fields.1206348510'
    When I PUT to "/admin/v2/customers/792145313" with json body:
      """
{
  "first_name" : "moe",
  "last_name" : "daanes",
  "email" : "daabes3333@gmail.com",
  "birthday" : "30-09-2015",
  "gender" : "male",
  "fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative
  Scenario: missing required field 'fields.1780108253'
    When I PUT to "/admin/v2/customers/792145313" with json body:
      """
{
  "first_name" : "moe",
  "last_name" : "daanes",
  "email" : "daabes3333@gmail.com",
  "birthday" : "30-09-2015",
  "gender" : "male",
  "fields" : {
    "1206348510" : "dddd"
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"
