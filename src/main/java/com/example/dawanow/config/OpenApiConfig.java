package com.example.dawanow.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String PRODUCT_PATH = "/api/v1/products";
    private static final String SORT_DESCRIPTION = "Sort using field,direction. Allowed fields: id, name, "
            + "arabicName, scientificName, price, company, route. Directions: asc or desc. "
            + "Repeat the sort parameter for multiple fields, for example: "
            + "sort=price,desc&sort=name,asc.";

    @Bean
    public OpenApiCustomizer bearerTokenSecurityScheme() {
        return openApi -> {
            var components = openApi.getComponents();
            if (components == null) return;

            var securityScheme = new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT");

            components.addSecuritySchemes("basicAuth", securityScheme);
        };
    }

    @Bean
    public OpenApiCustomizer productSortDocumentation() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
            if (!path.startsWith(PRODUCT_PATH)) {
                return;
            }

            documentSort(pathItem.getGet());
        });
    }

    private void documentSort(Operation operation) {
        if (operation == null || operation.getParameters() == null) {
            return;
        }

        operation.getParameters().stream()
                .filter(parameter -> "sort".equals(parameter.getName()))
                .forEach(this::documentSort);
    }

    private void documentSort(Parameter parameter) {
        parameter.setDescription(SORT_DESCRIPTION);
        parameter.setExample(List.of("price,desc"));
    }
}
