package com.example.yearend.deduction.application;

import com.example.yearend.deduction.domain.DeductionDecision;
import com.example.yearend.deduction.domain.DeductionItem;
import com.example.yearend.deduction.domain.TaxContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeductionEngine {

    private final DeductionPolicyRegistry deductionPolicyRegistry;

    public DeductionEngine(DeductionPolicyRegistry deductionPolicyRegistry) {
        this.deductionPolicyRegistry = deductionPolicyRegistry;
    }

    public List<DeductionDecision> evaluate(TaxContext context, List<DeductionItem> items) {
        return items.stream()
            .map(item -> deductionPolicyRegistry.get(item.getDeductionType()).evaluate(context, item))
            .toList();
    }
}
