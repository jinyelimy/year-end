package com.example.yearend.deduction.domain;

import java.util.List;
import java.util.Locale;

public record RuleSetSnapshot(
    String ruleSetId,
    String ruleVersion,
    String ruleSnapshotHash,
    boolean dbBacked,
    List<DeductionRule> rules
) {

    public RuleSetSnapshot {
        rules = List.copyOf(rules);
    }

    public static RuleSetSnapshot unresolved(int taxYear) {
        return new RuleSetSnapshot(
            taxYear + "@legacy-fallback",
            String.format(Locale.ROOT, "%04d.01", taxYear),
            "unresolved",
            false,
            List.of()
        );
    }

    public List<DeductionRule> rulesFor(DeductionType deductionType) {
        return rules.stream()
            .filter(rule -> rule.getDeductionType() == deductionType)
            .toList();
    }
}
