package support;

import io.restassured.module.jsv.JsonSchemaValidator;

import java.net.URL;

public class SchemaUtils {
    public static JsonSchemaValidator matchesSchema(String resourcePath) {
        URL schemaUrl = SchemaUtils.class.getClassLoader().getResource(resourcePath);
        if (schemaUrl == null) {
            throw new IllegalArgumentException("Schema not found on classpath: " + resourcePath);
        }
        return JsonSchemaValidator.matchesJsonSchema(schemaUrl);
    }
}

