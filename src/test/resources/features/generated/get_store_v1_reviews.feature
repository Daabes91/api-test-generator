Feature: GET /store/v1/reviews?type=products&format=lite&per_page=8&page=1
  Background:
    Given the API base url is "${BASE_URL}"
    And I add header "accept" with value 'application/json, text/plain, */*'
    And I add header "accept-language" with value 'ar'
    And I add header "cache-control" with value 'no-cache'
    And I add header "currency" with value 'SAR'
    And I add header "origin" with value 'https://salla.sa'
    And I add header "pragma" with value 'no-cache'
    And I add header "priority" with value 'u=1, i'
    And I add header "referer" with value 'https://salla.sa/'
    And I add header "s-app-os" with value 'browser'
    And I add header "s-app-version" with value '2.14.262'
    And I add header "s-country" with value 'JO'
    And I add header "s-ray" with value '100'
    And I add header "s-source" with value 'twilight'
    And I add header "s-store-api-version" with value 'swoole'
    And I add header "s-user-id" with value 'y3cJLqlt80YJYtMsZqy7wyfnJVaXpz52gNYdyNud'
    And I add header "s-version-id" with value '1414488713'
    And I add header "sec-ch-ua" with value '"Google Chrome";v="141", "Not?A_Brand";v="8", "Chromium";v="141'
    And I add header "sec-ch-ua-mobile" with value '?0'
    And I add header "sec-ch-ua-platform" with value '"macOS'
    And I add header "sec-fetch-dest" with value 'empty'
    And I add header "sec-fetch-mode" with value 'cors'
    And I add header "sec-fetch-site" with value 'cross-site'
    And I add header "store-identifier" with value '1328842359'
    And I add header "user-agent" with value 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36'
    And I add header "x-requested-with" with value 'XMLHttpRequest'

  @happy
  Scenario: Happy path generated from cURL
    When I GET to "/store/v1/reviews?type=products&format=lite&per_page=8&page=1"
    Then the response status should be 200
    Then the response header "Content-Type" should equal "application/json"

  @pagination
  Scenario: Page 1 returns results
    When I GET to "/store/v1/reviews?type=products&format=lite&per_page=8&page=1"
    Then the response status should be 200
    Then the response header "Content-Type" should equal "application/json"
    When I GET to "/store/v1/reviews?type=products&format=lite&per_page=8&page=1" and remember as "page1"
    Then the response status should be 200
    Then the response header "Content-Type" should equal "application/json"

  @pagination
  Scenario: Page 2 differs from Page 1
    When I GET to "/store/v1/reviews?type=products&format=lite&per_page=8&page=2"
    Then the response status should be 200
    Then the response header "Content-Type" should equal "application/json"
    Then the response body should not equal remembered "page1"
