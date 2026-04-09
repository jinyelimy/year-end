package com.example.yearend.deduction.infrastructure;

import com.example.yearend.deduction.domain.AbstractDeductionPolicy;
import com.example.yearend.deduction.domain.DeductionItem;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.TaxContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 보장성 보험료 세액공제 (소득세법 제59조의4)
 * - 공제 방식: 세액공제 (소득공제 아님)
 * - 한도: 연 1,000,000원
 * - 공제율: 12%
 * - 최대 세액공제: 120,000원
 */
@Component
public class InsurancePremiumDeductionPolicy extends AbstractDeductionPolicy {

    private static final long ANNUAL_LIMIT = 1_000_000L;
    private static final double CREDIT_RATE = 0.12;

    public InsurancePremiumDeductionPolicy() {
        super(List.of());
    }

    @Override
    public DeductionType supports() {
        return DeductionType.INSURANCE;
    }

    @Override
    protected long calculateEligibleAmount(TaxContext context, DeductionItem item) {
        // 보험료는 소득공제가 아니므로 소득공제 기여분은 0
        return 0L;
    }

    @Override
    protected long calculateTaxCredit(TaxContext context, DeductionItem item) {
        long capped = Math.min(item.getAmount(), ANNUAL_LIMIT);
        return Math.round(capped * CREDIT_RATE);
    }

    @Override
    protected String explainApplied(TaxContext context, DeductionItem item, long eligibleAmount, long appliedAmount) {
        long capped = Math.min(item.getAmount(), ANNUAL_LIMIT);
        long credit = Math.round(capped * CREDIT_RATE);
        return String.format(
            "보장성 보험료 세액공제 적용: 납입액 %,d원, 한도 적용 후 %,d원, 세액공제 %,d원 (공제율 12%%)",
            item.getAmount(), capped, credit
        );
    }
}
