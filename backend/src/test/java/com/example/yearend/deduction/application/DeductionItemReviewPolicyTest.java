package com.example.yearend.deduction.application;

import com.example.yearend.deduction.domain.DeductionItem;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.EvidenceStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeductionItemReviewPolicyTest {

    private final DeductionItemReviewPolicy policy = new DeductionItemReviewPolicy(new ObjectMapper());

    @Test
    @DisplayName("includes manual deduction items in calculation and checklist")
    void includesManualItems() {
        DeductionItem item = deductionItem(DeductionType.MEDICAL_EXPENSE, "{}");

        assertThat(policy.isImported(item)).isFalse();
        assertThat(policy.isIncludedInCalculation(item)).isTrue();
        assertThat(policy.isIncludedInDocumentChecklist(item)).isTrue();
    }

    @Test
    @DisplayName("includes approved imported items in calculation and checklist")
    void includesApprovedImportedItems() {
        DeductionItem item = deductionItem(
            DeductionType.INSURANCE,
            """
                {"sourceType":"HOMETAX","reviewStatus":"APPROVED"}
                """
        );

        assertThat(policy.isImported(item)).isTrue();
        assertThat(policy.isIncludedInCalculation(item)).isTrue();
        assertThat(policy.isIncludedInDocumentChecklist(item)).isTrue();
    }

    @Test
    @DisplayName("excludes pending imported items from calculation and checklist")
    void excludesPendingImportedItems() {
        DeductionItem item = deductionItem(
            DeductionType.INSURANCE,
            """
                {"sourceType":"HOMETAX","reviewStatus":"PENDING"}
                """
        );

        assertThat(policy.isIncludedInCalculation(item)).isFalse();
        assertThat(policy.isIncludedInDocumentChecklist(item)).isFalse();
    }

    @Test
    @DisplayName("excludes explicitly excluded imported items from calculation and checklist")
    void excludesRejectedImportedItems() {
        DeductionItem item = deductionItem(
            DeductionType.INSURANCE,
            """
                {"sourceType":"HOMETAX","reviewStatus":"EXCLUDED"}
                """
        );

        assertThat(policy.isIncludedInCalculation(item)).isFalse();
        assertThat(policy.isIncludedInDocumentChecklist(item)).isFalse();
    }

    @Test
    @DisplayName("excludes imported items that are stored for review only until policy support exists")
    void excludesPolicyUnsupportedImportedItems() {
        DeductionItem item = deductionItem(
            DeductionType.CREDIT_CARD,
            """
                {"sourceType":"HOMETAX","reviewStatus":"APPROVED","calculationSupported":false}
                """
        );

        assertThat(policy.isIncludedInCalculation(item)).isFalse();
        assertThat(policy.isIncludedInDocumentChecklist(item)).isFalse();
    }

    private DeductionItem deductionItem(DeductionType deductionType, String attributesJsonb) {
        DeductionItem item = new DeductionItem();
        item.setDeductionType(deductionType);
        item.setAmount(100_000L);
        item.setEvidenceStatus(EvidenceStatus.SUBMITTED);
        item.setAttributesJsonb(attributesJsonb);
        return item;
    }
}
