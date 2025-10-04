
import io.cucumber.core.cli.Main;

public class CucumberRunner {
  public static void main(String[] args) {
    System.out.println("Cucumber feature: src/test/resources/features/generated/customer_from_dataset.feature");
    String[] cucumberArgs = new String[] {
      "src/test/resources/features/generated/customer_from_dataset.feature",
      "--glue", "steps",
      "--plugin", "pretty"
    };
    Main.main(cucumberArgs);
  }
}