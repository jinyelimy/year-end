package com.example.yearend.deduction.infrastructure;

import com.example.yearend.deduction.domain.DeductionItem;
import com.example.yearend.deduction.domain.EligibilityCheckResult;
import com.example.yearend.deduction.domain.EligibilityChecker;
import com.example.yearend.deduction.domain.TaxContext;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class EducationInstitutionChecker implements EligibilityChecker {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
        "PRESCHOOL",
        "SCHOOL",
        "UNIVERSITY",
        "ACADEMY",
        "SELF_EDUCATION",
        "UNIFORM"
    );

    @Override
    public EligibilityCheckResult check(TaxContext context, DeductionItem item) {
        if (item.getSubType() == null || item.getSubType().isBlank()) {
            return EligibilityCheckResult.fail("Education expense subtype is required.");
        }

        if (!SUPPORTED_TYPES.contains(item.getSubType().trim().toUpperCase())) {
            return EligibilityCheckResult.fail("Unsupported education institution type.");
        }

        return EligibilityCheckResult.pass("Education institution type is supported.");
    }
}
