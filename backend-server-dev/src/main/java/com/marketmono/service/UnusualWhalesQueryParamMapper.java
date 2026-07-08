package com.marketmono.service;

import com.marketmono.dto.UnusualWhalesParam;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.BindParam;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Maps record-based request DTOs to upstream market data provider query parameter maps.
 */
@Component
public class UnusualWhalesQueryParamMapper {

    public Map<String, Object> toParams(MultiValueMap<String, String> requestParams) {
        Map<String, Object> params = new LinkedHashMap<>();

        requestParams.forEach((key, values) -> {
            if (key == null || key.isBlank()) {
                return;
            }

            List<String> normalizedValues = values == null
                ? List.of()
                : values.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .toList();

            if (normalizedValues.isEmpty()) {
                return;
            }

            params.put(key, normalizedValues.size() == 1
                ? normalizedValues.getFirst()
                : String.join(",", normalizedValues));
        });

        return params;
    }

    public Map<String, Object> toParams(Record request) {
        Map<String, Object> params = new LinkedHashMap<>();

        for (RecordComponent component : request.getClass().getRecordComponents()) {
            Object value = readValue(request, component);
            if (!hasValue(value)) {
                continue;
            }
            params.put(resolveParamName(component), value);
        }

        return params;
    }

    private String resolveParamName(RecordComponent component) {
        UnusualWhalesParam upstreamParam = component.getAnnotation(UnusualWhalesParam.class);
        if (upstreamParam != null) {
            return upstreamParam.value();
        }

        BindParam bindParam = resolveBindParam(component);
        if (bindParam != null) {
            return bindParam.value();
        }

        return component.getName();
    }

    private BindParam resolveBindParam(RecordComponent component) {
        BindParam bindParam = component.getAnnotation(BindParam.class);
        if (bindParam != null) {
            return bindParam;
        }

        bindParam = component.getAccessor().getAnnotation(BindParam.class);
        if (bindParam != null) {
            return bindParam;
        }

        return resolveCanonicalConstructorBindParam(component);
    }

    private BindParam resolveCanonicalConstructorBindParam(RecordComponent component) {
        RecordComponent[] components = component.getDeclaringRecord().getRecordComponents();
        int componentIndex = IntStream.range(0, components.length)
            .filter(index -> components[index].getName().equals(component.getName()))
            .findFirst()
            .orElse(-1);
        if (componentIndex < 0) {
            return null;
        }

        Class<?>[] parameterTypes = Arrays.stream(components)
            .map(RecordComponent::getType)
            .toArray(Class<?>[]::new);

        try {
            Constructor<?> constructor = component.getDeclaringRecord().getDeclaredConstructor(parameterTypes);
            Parameter parameter = constructor.getParameters()[componentIndex];
            return parameter.getAnnotation(BindParam.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Failed to inspect canonical record constructor: "
                + component.getDeclaringRecord().getName(), e);
        }
    }

    private Object readValue(Record request, RecordComponent component) {
        try {
            return component.getAccessor().invoke(request);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to read query parameter component: " + component.getName(), e);
        }
    }

    private boolean hasValue(Object value) {
        return switch (value) {
            case null -> false;
            case CharSequence chars -> !chars.toString().isBlank();
            case Collection<?> collection -> !collection.isEmpty();
            default -> true;
        };
    }
}
