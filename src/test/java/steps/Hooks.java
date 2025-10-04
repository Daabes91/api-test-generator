package steps;

import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.qameta.allure.Allure;
import io.qameta.allure.model.Label;

import java.util.Locale;
import java.util.Collection;

public class Hooks {

    @Before
    public void applyAllureLabels(Scenario scenario) {
        support.World.reset();
        try {
            Collection<String> tags = scenario.getSourceTagNames();
            if (tags == null || tags.isEmpty()) return;

            for (String raw : tags) {
                if (raw == null) continue;
                String t = raw.trim();
                if (!t.startsWith("@")) continue;
                t = t.substring(1); // drop '@'
                int colon = t.indexOf(':');
                if (colon <= 0 || colon == t.length() - 1) continue;
                String key = t.substring(0, colon).toLowerCase(Locale.ROOT).trim();
                String value = t.substring(colon + 1).trim();
                if (value.isEmpty()) continue;

                switch (key) {
                    case "epic", "feature", "story" -> addLabel(key, value);
                    case "severity" -> addLabel("severity", value.toLowerCase(Locale.ROOT));
                    case "owner", "framework", "layer" -> addLabel(key, value);
                }
            }
        } catch (Throwable ignore) {}
    }

    private void addLabel(String name, String value) {
        try {
            Allure.getLifecycle().updateTestCase(tc -> tc.getLabels().add(new Label().setName(name).setValue(value)));
        } catch (Throwable ignore) {}
    }
}
