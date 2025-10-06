Feature: POST /store/v1/profile/update/
  Background:
    Given the API base url is "${BASE_URL}"
    And I use bearer token from env "store_customer_token"
    And I add header "accept" with value 'application/json, text/plain, */*'
    And I add header "accept-language" with value 'ar'
    And I add header "cache-control" with value 'no-cache'
    And I add header "currency" with value 'SAR'
    And I add header "origin" with value 'https://salla.sa'
    And I add header "pragma" with value 'no-cache'
    And I add header "priority" with value 'u=1, i'
    And I add header "referer" with value 'https://salla.sa/'
    And I add header "s-app-os" with value 'browser'
    And I add header "s-app-version" with value 'v2.0.0'
    And I add header "s-country" with value 'JO'
    And I add header "s-no-cache" with value 'true'
    And I add header "s-ray" with value '100'
    And I add header "s-source" with value 'twilight'
    And I add header "s-theme-id" with value '1298199463'
    And I add header "s-theme-name" with value '1298199463'
    And I add header "s-user-id" with value '455300974'
    And I add header "s-version-id" with value '1223257969'
    And I add header "sec-ch-ua" with value '"Chromium";v="140", "Not=A?Brand";v="24", "Google Chrome";v="140'
    And I add header "sec-ch-ua-mobile" with value '?0'
    And I add header "sec-ch-ua-platform" with value '"macOS'
    And I add header "sec-fetch-dest" with value 'empty'
    And I add header "sec-fetch-mode" with value 'cors'
    And I add header "sec-fetch-site" with value 'cross-site'
    And I add header "store-identifier" with value '1328842359'
    And I add header "user-agent" with value 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36'
    And I add header "x-requested-with" with value 'XMLHttpRequest'
    And I add header "content-type" with value 'application/json'

  @happy
  Scenario: Happy path generated from cURL
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name": "mohammadx",
  "last_name": "daabes",
  "birthday": "2015-09-08",
  "gender": "male",
  "email": "mohammad.daabes3@gmail.com",
  "phone": "799798713",
  "country_code": "JO",
  "custom_fields": {
    "1780108253": "",
    "1206348510": "sss"
  },
  "hidden-custom_fields": {
    "1780108253": ""
  }
}
      """
    Then the response status should be 200
    Then the response header "Content-Type" should equal "application/json"

  @auth
  Scenario: Missing token returns 401
    Given I remove authorization header
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name": "mohammadx",
  "last_name": "daabes",
  "birthday": "2015-09-08",
  "gender": "male",
  "email": "mohammad.daabes3@gmail.com",
  "phone": "799798713",
  "country_code": "JO",
  "custom_fields": {
    "1780108253": "",
    "1206348510": "sss"
  },
  "hidden-custom_fields": {
    "1780108253": ""
  }
}
      """
    Then the response status should be 401
    Then the response header "Content-Type" should equal "application/json"

  @negative
  Scenario: missing required field 'first_name'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "last_name" : "daabes",
  "birthday" : "2015-09-08",
  "gender" : "male",
  "email" : "mohammad.daabes3@gmail.com",
  "phone" : "799798713",
  "country_code" : "JO",
  "custom_fields" : {
    "1780108253" : "",
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative
  Scenario: missing required field 'last_name'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "birthday" : "2015-09-08",
  "gender" : "male",
  "email" : "mohammad.daabes3@gmail.com",
  "phone" : "799798713",
  "country_code" : "JO",
  "custom_fields" : {
    "1780108253" : "",
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative
  Scenario: missing required field 'birthday'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "last_name" : "daabes",
  "gender" : "male",
  "email" : "mohammad.daabes3@gmail.com",
  "phone" : "799798713",
  "country_code" : "JO",
  "custom_fields" : {
    "1780108253" : "",
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative
  Scenario: missing required field 'gender'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "last_name" : "daabes",
  "birthday" : "2015-09-08",
  "email" : "mohammad.daabes3@gmail.com",
  "phone" : "799798713",
  "country_code" : "JO",
  "custom_fields" : {
    "1780108253" : "",
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative
  Scenario: missing required field 'email'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "last_name" : "daabes",
  "birthday" : "2015-09-08",
  "gender" : "male",
  "phone" : "799798713",
  "country_code" : "JO",
  "custom_fields" : {
    "1780108253" : "",
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative
  Scenario: missing required field 'phone'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "last_name" : "daabes",
  "birthday" : "2015-09-08",
  "gender" : "male",
  "email" : "mohammad.daabes3@gmail.com",
  "country_code" : "JO",
  "custom_fields" : {
    "1780108253" : "",
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative
  Scenario: missing required field 'country_code'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "last_name" : "daabes",
  "birthday" : "2015-09-08",
  "gender" : "male",
  "email" : "mohammad.daabes3@gmail.com",
  "phone" : "799798713",
  "custom_fields" : {
    "1780108253" : "",
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative
  Scenario: missing required field 'custom_fields.1780108253'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "last_name" : "daabes",
  "birthday" : "2015-09-08",
  "gender" : "male",
  "email" : "mohammad.daabes3@gmail.com",
  "phone" : "799798713",
  "country_code" : "JO",
  "custom_fields" : {
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative
  Scenario: missing required field 'custom_fields.1206348510'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "last_name" : "daabes",
  "birthday" : "2015-09-08",
  "gender" : "male",
  "email" : "mohammad.daabes3@gmail.com",
  "phone" : "799798713",
  "country_code" : "JO",
  "custom_fields" : {
    "1780108253" : ""
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative
  Scenario: missing required field 'hidden-custom_fields.1780108253'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "last_name" : "daabes",
  "birthday" : "2015-09-08",
  "gender" : "male",
  "email" : "mohammad.daabes3@gmail.com",
  "phone" : "799798713",
  "country_code" : "JO",
  "custom_fields" : {
    "1780108253" : "",
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : { }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative @datatype
  Scenario: invalid type for 'first_name'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : 123,
  "last_name" : "daabes",
  "birthday" : "2015-09-08",
  "gender" : "male",
  "email" : "mohammad.daabes3@gmail.com",
  "phone" : "799798713",
  "country_code" : "JO",
  "custom_fields" : {
    "1780108253" : "",
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative @datatype
  Scenario: invalid type for 'last_name'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "last_name" : 123,
  "birthday" : "2015-09-08",
  "gender" : "male",
  "email" : "mohammad.daabes3@gmail.com",
  "phone" : "799798713",
  "country_code" : "JO",
  "custom_fields" : {
    "1780108253" : "",
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative @datatype
  Scenario: invalid type for 'birthday'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "last_name" : "daabes",
  "birthday" : 123,
  "gender" : "male",
  "email" : "mohammad.daabes3@gmail.com",
  "phone" : "799798713",
  "country_code" : "JO",
  "custom_fields" : {
    "1780108253" : "",
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative @datatype
  Scenario: invalid type for 'gender'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "last_name" : "daabes",
  "birthday" : "2015-09-08",
  "gender" : 123,
  "email" : "mohammad.daabes3@gmail.com",
  "phone" : "799798713",
  "country_code" : "JO",
  "custom_fields" : {
    "1780108253" : "",
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative @datatype
  Scenario: invalid type for 'email'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "last_name" : "daabes",
  "birthday" : "2015-09-08",
  "gender" : "male",
  "email" : 123,
  "phone" : "799798713",
  "country_code" : "JO",
  "custom_fields" : {
    "1780108253" : "",
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative @datatype
  Scenario: invalid type for 'phone'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "last_name" : "daabes",
  "birthday" : "2015-09-08",
  "gender" : "male",
  "email" : "mohammad.daabes3@gmail.com",
  "phone" : 123,
  "country_code" : "JO",
  "custom_fields" : {
    "1780108253" : "",
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative @datatype
  Scenario: invalid type for 'country_code'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "last_name" : "daabes",
  "birthday" : "2015-09-08",
  "gender" : "male",
  "email" : "mohammad.daabes3@gmail.com",
  "phone" : "799798713",
  "country_code" : 123,
  "custom_fields" : {
    "1780108253" : "",
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative @datatype
  Scenario: invalid type for 'custom_fields.1780108253'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "last_name" : "daabes",
  "birthday" : "2015-09-08",
  "gender" : "male",
  "email" : "mohammad.daabes3@gmail.com",
  "phone" : "799798713",
  "country_code" : "JO",
  "custom_fields" : {
    "1780108253" : 123,
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative @datatype
  Scenario: invalid type for 'custom_fields.1206348510'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "last_name" : "daabes",
  "birthday" : "2015-09-08",
  "gender" : "male",
  "email" : "mohammad.daabes3@gmail.com",
  "phone" : "799798713",
  "country_code" : "JO",
  "custom_fields" : {
    "1780108253" : "",
    "1206348510" : 123
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative @datatype
  Scenario: invalid type for 'hidden-custom_fields.1780108253'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "last_name" : "daabes",
  "birthday" : "2015-09-08",
  "gender" : "male",
  "email" : "mohammad.daabes3@gmail.com",
  "phone" : "799798713",
  "country_code" : "JO",
  "custom_fields" : {
    "1780108253" : "",
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : {
    "1780108253" : 123
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative @null
  Scenario: null value for 'first_name'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : null,
  "last_name" : "daabes",
  "birthday" : "2015-09-08",
  "gender" : "male",
  "email" : "mohammad.daabes3@gmail.com",
  "phone" : "799798713",
  "country_code" : "JO",
  "custom_fields" : {
    "1780108253" : "",
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative @null
  Scenario: null value for 'last_name'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "last_name" : null,
  "birthday" : "2015-09-08",
  "gender" : "male",
  "email" : "mohammad.daabes3@gmail.com",
  "phone" : "799798713",
  "country_code" : "JO",
  "custom_fields" : {
    "1780108253" : "",
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative @null
  Scenario: null value for 'birthday'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "last_name" : "daabes",
  "birthday" : null,
  "gender" : "male",
  "email" : "mohammad.daabes3@gmail.com",
  "phone" : "799798713",
  "country_code" : "JO",
  "custom_fields" : {
    "1780108253" : "",
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative @null
  Scenario: null value for 'gender'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "last_name" : "daabes",
  "birthday" : "2015-09-08",
  "gender" : null,
  "email" : "mohammad.daabes3@gmail.com",
  "phone" : "799798713",
  "country_code" : "JO",
  "custom_fields" : {
    "1780108253" : "",
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative @null
  Scenario: null value for 'email'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "last_name" : "daabes",
  "birthday" : "2015-09-08",
  "gender" : "male",
  "email" : null,
  "phone" : "799798713",
  "country_code" : "JO",
  "custom_fields" : {
    "1780108253" : "",
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative @null
  Scenario: null value for 'phone'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "last_name" : "daabes",
  "birthday" : "2015-09-08",
  "gender" : "male",
  "email" : "mohammad.daabes3@gmail.com",
  "phone" : null,
  "country_code" : "JO",
  "custom_fields" : {
    "1780108253" : "",
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative @null
  Scenario: null value for 'country_code'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "last_name" : "daabes",
  "birthday" : "2015-09-08",
  "gender" : "male",
  "email" : "mohammad.daabes3@gmail.com",
  "phone" : "799798713",
  "country_code" : null,
  "custom_fields" : {
    "1780108253" : "",
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative @null
  Scenario: null value for 'custom_fields.1780108253'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "last_name" : "daabes",
  "birthday" : "2015-09-08",
  "gender" : "male",
  "email" : "mohammad.daabes3@gmail.com",
  "phone" : "799798713",
  "country_code" : "JO",
  "custom_fields" : {
    "1780108253" : null,
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative @null
  Scenario: null value for 'custom_fields.1206348510'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "last_name" : "daabes",
  "birthday" : "2015-09-08",
  "gender" : "male",
  "email" : "mohammad.daabes3@gmail.com",
  "phone" : "799798713",
  "country_code" : "JO",
  "custom_fields" : {
    "1780108253" : "",
    "1206348510" : null
  },
  "hidden-custom_fields" : {
    "1780108253" : ""
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"

  @negative @null
  Scenario: null value for 'hidden-custom_fields.1780108253'
    When I POST to "/store/v1/profile/update/" with json body:
      """
{
  "first_name" : "mohammadx",
  "last_name" : "daabes",
  "birthday" : "2015-09-08",
  "gender" : "male",
  "email" : "mohammad.daabes3@gmail.com",
  "phone" : "799798713",
  "country_code" : "JO",
  "custom_fields" : {
    "1780108253" : "",
    "1206348510" : "sss"
  },
  "hidden-custom_fields" : {
    "1780108253" : null
  }
}
      """
    Then the response status should be 400
    Then the response header "Content-Type" should equal "application/json"
