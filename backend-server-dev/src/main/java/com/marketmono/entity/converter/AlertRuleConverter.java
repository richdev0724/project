package com.marketmono.entity.converter;

import com.marketmono.entity.AlertRule;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AlertRuleConverter implements AttributeConverter<AlertRule, String> {
    
    @Override
    public String convertToDatabaseColumn(AlertRule attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name();
    }
    
    @Override
    public AlertRule convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        try {
            return AlertRule.valueOf(dbData);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
