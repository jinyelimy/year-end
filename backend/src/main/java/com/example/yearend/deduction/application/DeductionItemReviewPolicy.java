package com.example.yearend.deduction.application;

import com.example.yearend.deduction.domain.DeductionItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class DeductionItemReviewPolicy {

    private static final TypeReference<Map<String, Object>> ATTRIBUTES_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public boolean isImported(DeductionItem item) {
        return "HOMETAX".equals(readAttributes(item).get("sourceType"));
    }

    public boolean isIncludedInCalculation(DeductionItem item) {
        if (!isImported(item)) {
            return true;
        }

        Object reviewStatus = readAttributes(item).get("reviewStatus");
        return !"PENDING".equals(reviewStatus) && !"EXCLUDED".equals(reviewStatus);
    }

    public boolean isIncludedInDocumentChecklist(DeductionItem item) {
        return isIncludedInCalculation(item);
    }

    public Map<String, Object> readAttributes(DeductionItem item) {
        try {
            return objectMapper.readValue(
                Objects.requireNonNullElse(item.getAttributesJsonb(), "{}"),
                ATTRIBUTES_TYPE
            );
        } catch (IOException exception) {
            return Map.of();
        }
    }
}
