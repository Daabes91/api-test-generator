Feature: GET /store/v1/auth/user/?use_username_url=1
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

  @happy
  Scenario: Happy path generated from cURL
    When I GET to "/store/v1/auth/user/?use_username_url=1"
    Then the response status should be 200
    Then the response header "Content-Type" should equal "application/json"
    Then the response json path "$.status" should equal "200"
    Then the response json path "$.success" should equal "true"
    Then the response json path "$.data.id" should equal "455300974"
    Then the response json path "$.data.first_name" should contain "mohammadxmohamm"
    Then the response json path "$.data.email" should equal "mohammad.daabes3@gmail.com"

  @auth
  Scenario: Missing token returns 401
    Given I remove authorization header
    When I GET to "/store/v1/auth/user/?use_username_url=1"
    Then the response status should be 401
    Then the response header "Content-Type" should equal "application/json"
