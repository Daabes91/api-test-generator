Feature: POST /admin/v2/customers
  Background:
    Given the API base url is "${BASE_URL}"
    And I use bearer token from env "API_TOKEN_DASHBOARD_ADMIN"
    And I add header "s-source" with value 'merchant-dashboard'
    And I add header "s-store-id" with value '783386284'
    And I add header "accept-language" with value 'ar'

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
    Then the response json path "$.status" should equal "200"
    Then the response json path "$.success" should equal "true"
    Then the response json path "$.data.id" should equal "1022096017"

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
    Then the response json path "$.status" should equal "401"
    Then the response json path "$.success" should equal "false"
    Then the response json path "$.error.code" should equal "Invalid-token"
    Then the response json path "$.error.message" should equal "please provide a valid API Key"
    Then the response json path "$.status" should equal "200"
    Then the response json path "$.success" should equal "true"
    Then the response json path "$.data.id" should equal "1022096017"
    Then the response json should equal:
      """
{
    "status": 401,
    "success": false,
    "error": {
        "code": "Invalid-token",
        "message": "please provide a valid API Key"
    }
}
      """

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
    Then the response status should be 422
    Then the response header "Content-Type" should equal "application/json"
    Then the response json path "$.status" should equal "200"
    Then the response json path "$.success" should equal "true"
    Then the response json path "$.data.id" should equal "1022096017"

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
    Then the response status should be 403
    Then the response header "Content-Type" should equal "application/json"
    Then the response json path "$.status" should equal "200"
    Then the response json path "$.success" should equal "true"
    Then the response json path "$.data.id" should equal "1022096017"

  @negative @datatype
  Scenario: invalid type for 'first_name'
    When I POST to "/admin/v2/customers" with json body:
      """
{
  "first_name" : 123,
  "last_name" : " daabesdaabesdas",
  "email" : "m.daabes+93253@salla.sa",
  "birthday" : "08-09-2015",
  "gender" : "male",
  "mobile_code_country" : "962",
  "mobile" : "799798444"
}
      """
    Then the response status should be 422
    Then the response header "Content-Type" should equal "application/json"
    Then the response json path "$.status" should equal "200"
    Then the response json path "$.success" should equal "true"
    Then the response json path "$.data.id" should equal "1022096017"

  @negative @datatype
  Scenario: invalid type for 'last_name'
    When I POST to "/admin/v2/customers" with json body:
      """
{
  "first_name" : "moe",
  "last_name" : 123,
  "email" : "m.daabes+93253@salla.sa",
  "birthday" : "08-09-2015",
  "gender" : "male",
  "mobile_code_country" : "962",
  "mobile" : "799798444"
}
      """
    Then the response status should be 422
    Then the response header "Content-Type" should equal "application/json"
    Then the response json path "$.status" should equal "200"
    Then the response json path "$.success" should equal "true"
    Then the response json path "$.data.id" should equal "1022096017"

  @negative @datatype
  Scenario: invalid type for 'email'
    When I POST to "/admin/v2/customers" with json body:
      """
{
  "first_name" : "moe",
  "last_name" : " daabesdaabesdas",
  "email" : 123,
  "birthday" : "08-09-2015",
  "gender" : "male",
  "mobile_code_country" : "962",
  "mobile" : "799798444"
}
      """
    Then the response status should be 422
    Then the response header "Content-Type" should equal "application/json"
    Then the response json path "$.status" should equal "200"
    Then the response json path "$.success" should equal "true"
    Then the response json path "$.data.id" should equal "1022096017"

  @negative @datatype
  Scenario: invalid type for 'birthday'
    When I POST to "/admin/v2/customers" with json body:
      """
{
  "first_name" : "moe",
  "last_name" : " daabesdaabesdas",
  "email" : "m.daabes+93253@salla.sa",
  "birthday" : 123,
  "gender" : "male",
  "mobile_code_country" : "962",
  "mobile" : "799798444"
}
      """
    Then the response status should be 422
    Then the response header "Content-Type" should equal "application/json"
    Then the response json path "$.status" should equal "200"
    Then the response json path "$.success" should equal "true"
    Then the response json path "$.data.id" should equal "1022096017"

  @negative @datatype
  Scenario: invalid type for 'gender'
    When I POST to "/admin/v2/customers" with json body:
      """
{
  "first_name" : "moe",
  "last_name" : " daabesdaabesdas",
  "email" : "m.daabes+93253@salla.sa",
  "birthday" : "08-09-2015",
  "gender" : 123,
  "mobile_code_country" : "962",
  "mobile" : "799798444"
}
      """
    Then the response status should be 422
    Then the response header "Content-Type" should equal "application/json"
    Then the response json path "$.status" should equal "200"
    Then the response json path "$.success" should equal "true"
    Then the response json path "$.data.id" should equal "1022096017"

  @negative @datatype
  Scenario: invalid type for 'mobile_code_country'
    When I POST to "/admin/v2/customers" with json body:
      """
{
  "first_name" : "moe",
  "last_name" : " daabesdaabesdas",
  "email" : "m.daabes+93253@salla.sa",
  "birthday" : "08-09-2015",
  "gender" : "male",
  "mobile_code_country" : 123,
  "mobile" : "799798444"
}
      """
    Then the response status should be 422
    Then the response header "Content-Type" should equal "application/json"
    Then the response json path "$.status" should equal "200"
    Then the response json path "$.success" should equal "true"
    Then the response json path "$.data.id" should equal "1022096017"

  @negative @datatype
  Scenario: invalid type for 'mobile'
    When I POST to "/admin/v2/customers" with json body:
      """
{
  "first_name" : "moe",
  "last_name" : " daabesdaabesdas",
  "email" : "m.daabes+93253@salla.sa",
  "birthday" : "08-09-2015",
  "gender" : "male",
  "mobile_code_country" : "962",
  "mobile" : 123
}
      """
    Then the response status should be 422
    Then the response header "Content-Type" should equal "application/json"
    Then the response json path "$.status" should equal "200"
    Then the response json path "$.success" should equal "true"
    Then the response json path "$.data.id" should equal "1022096017"

  @negative @null
  Scenario: null value for 'first_name'
    When I POST to "/admin/v2/customers" with json body:
      """
{
  "first_name" : null,
  "last_name" : " daabesdaabesdas",
  "email" : "m.daabes+93253@salla.sa",
  "birthday" : "08-09-2015",
  "gender" : "male",
  "mobile_code_country" : "962",
  "mobile" : "799798444"
}
      """
    Then the response status should be 422
    Then the response header "Content-Type" should equal "application/json"
    Then the response json path "$.status" should equal "200"
    Then the response json path "$.success" should equal "true"
    Then the response json path "$.data.id" should equal "1022096017"

  @negative @null
  Scenario: null value for 'last_name'
    When I POST to "/admin/v2/customers" with json body:
      """
{
  "first_name" : "moe",
  "last_name" : null,
  "email" : "m.daabes+93253@salla.sa",
  "birthday" : "08-09-2015",
  "gender" : "male",
  "mobile_code_country" : "962",
  "mobile" : "799798444"
}
      """
    Then the response status should be 422
    Then the response header "Content-Type" should equal "application/json"
    Then the response json path "$.status" should equal "200"
    Then the response json path "$.success" should equal "true"
    Then the response json path "$.data.id" should equal "1022096017"

  @negative @null
  Scenario: null value for 'email'
    When I POST to "/admin/v2/customers" with json body:
      """
{
  "first_name" : "moe",
  "last_name" : " daabesdaabesdas",
  "email" : null,
  "birthday" : "08-09-2015",
  "gender" : "male",
  "mobile_code_country" : "962",
  "mobile" : "799798444"
}
      """
    Then the response status should be 422
    Then the response header "Content-Type" should equal "application/json"
    Then the response json path "$.status" should equal "200"
    Then the response json path "$.success" should equal "true"
    Then the response json path "$.data.id" should equal "1022096017"

  @negative @null
  Scenario: null value for 'birthday'
    When I POST to "/admin/v2/customers" with json body:
      """
{
  "first_name" : "moe",
  "last_name" : " daabesdaabesdas",
  "email" : "m.daabes+93253@salla.sa",
  "birthday" : null,
  "gender" : "male",
  "mobile_code_country" : "962",
  "mobile" : "799798444"
}
      """
    Then the response status should be 422
    Then the response header "Content-Type" should equal "application/json"
    Then the response json path "$.status" should equal "200"
    Then the response json path "$.success" should equal "true"
    Then the response json path "$.data.id" should equal "1022096017"

  @negative @null
  Scenario: null value for 'gender'
    When I POST to "/admin/v2/customers" with json body:
      """
{
  "first_name" : "moe",
  "last_name" : " daabesdaabesdas",
  "email" : "m.daabes+93253@salla.sa",
  "birthday" : "08-09-2015",
  "gender" : null,
  "mobile_code_country" : "962",
  "mobile" : "799798444"
}
      """
    Then the response status should be 422
    Then the response header "Content-Type" should equal "application/json"
    Then the response json path "$.status" should equal "200"
    Then the response json path "$.success" should equal "true"
    Then the response json path "$.data.id" should equal "1022096017"

  @negative @null
  Scenario: null value for 'mobile_code_country'
    When I POST to "/admin/v2/customers" with json body:
      """
{
  "first_name" : "moe",
  "last_name" : " daabesdaabesdas",
  "email" : "m.daabes+93253@salla.sa",
  "birthday" : "08-09-2015",
  "gender" : "male",
  "mobile_code_country" : null,
  "mobile" : "799798444"
}
      """
    Then the response status should be 422
    Then the response header "Content-Type" should equal "application/json"
    Then the response json path "$.status" should equal "200"
    Then the response json path "$.success" should equal "true"
    Then the response json path "$.data.id" should equal "1022096017"

  @negative @null
  Scenario: null value for 'mobile'
    When I POST to "/admin/v2/customers" with json body:
      """
{
  "first_name" : "moe",
  "last_name" : " daabesdaabesdas",
  "email" : "m.daabes+93253@salla.sa",
  "birthday" : "08-09-2015",
  "gender" : "male",
  "mobile_code_country" : "962",
  "mobile" : null
}
      """
    Then the response status should be 422
    Then the response header "Content-Type" should equal "application/json"
    Then the response json path "$.status" should equal "200"
    Then the response json path "$.success" should equal "true"
    Then the response json path "$.data.id" should equal "1022096017"

  @negative
  Scenario: invalid length for 'first_name' (too short)
    When I POST to "/admin/v2/customers" with json body:
      """
{
  "first_name" : "mm",
  "last_name" : " daabesdaabesdas",
  "email" : "m.daabes+93253@salla.sa",
  "birthday" : "08-09-2015",
  "gender" : "male",
  "mobile_code_country" : "962",
  "mobile" : "799798444"
}
      """
    Then the response status should be 422
    Then the response header "Content-Type" should equal "application/json"
    Then the response json path "$.status" should equal "200"
    Then the response json path "$.success" should equal "true"
    Then the response json path "$.data.id" should equal "1022096017"
    Then the response json path "$.status" should equal "401"
    Then the response json path "$.success" should equal "false"
    Then the response json path "$.error.code" should equal "Invalid-token"
    Then the response json path "$.error.message" should equal "please provide a valid API Key"

  @negative
  Scenario: invalid length for 'first_name' (too long)
    When I POST to "/admin/v2/customers" with json body:
      """
{
  "first_name" : "mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm",
  "last_name" : " daabesdaabesdas",
  "email" : "m.daabes+93253@salla.sa",
  "birthday" : "08-09-2015",
  "gender" : "male",
  "mobile_code_country" : "962",
  "mobile" : "799798444"
}
      """
    Then the response status should be 422
    Then the response header "Content-Type" should equal "application/json"
    Then the response json path "$.status" should equal "200"
    Then the response json path "$.success" should equal "true"
    Then the response json path "$.data.id" should equal "1022096017"
    Then the response json path "$.status" should equal "401"
    Then the response json path "$.success" should equal "false"
    Then the response json path "$.error.code" should equal "Invalid-token"
    Then the response json path "$.error.message" should equal "please provide a valid API Key"
