package com.example.yearend.deduction.application;

import com.example.yearend.deduction.domain.DeductionRule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RuleSnapshotHasher {

    private final ObjectMapper objectMapper;

    public String hash(int taxYear, String canonicalRuleVersion, List<DeductionRule> rules) {
        try {
            List<RulePayload> normalizedRules = rules.stream()
                .sorted(ruleComparator())
                .map(rule -> new RulePayload(
                    rule.getTaxYear(),
                    rule.getDeductionType().name(),
                    rule.getRuleCode(),
                    rule.getRuleName(),
                    rule.getRuleVersion(),
                    rule.getRuleCategory().name(),
                    rule.getParameterJsonb(),
                    rule.getDescription(),
                    rule.getEffectiveFrom().toString(),
                    rule.getEffectiveTo() == null ? null : rule.getEffectiveTo().toString()
                ))
                .toList();

            String payload = objectMapper.writeValueAsString(
                new SnapshotPayload(
                    taxYear,
                    canonicalRuleVersion,
                    normalizedRules
                )
            );
            return HexFormat.of().formatHex(sha256(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize the resolved rule snapshot.", exception);
        }
    }

    private Comparator<DeductionRule> ruleComparator() {
        return Comparator
            .comparing(DeductionRule::getEffectiveFrom)
            .thenComparing(rule -> rule.getDeductionType().name())
            .thenComparing(DeductionRule::getRuleCode);
    }

    private byte[] sha256(String payload) {
        try {
            return MessageDigest.getInstance("SHA-256")
                .digest(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Failed to initialize SHA-256.", exception);
        }
    }

    private record SnapshotPayload(
        int taxYear,
        String ruleVersion,
        List<RulePayload> rules
    ) {
    }

    private record RulePayload(
        Integer taxYear,
        String deductionType,
        String ruleCode,
        String ruleName,
        Integer storageRuleVersion,
        String ruleCategory,
        String parameterJsonb,
        String description,
        String effectiveFrom,
        String effectiveTo
    ) {
    }
}
