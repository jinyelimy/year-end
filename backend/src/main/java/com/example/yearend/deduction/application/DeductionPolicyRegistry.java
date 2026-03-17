package com.example.yearend.deduction.application;

import com.example.yearend.common.exception.BusinessException;
import com.example.yearend.common.exception.ErrorCode;
import com.example.yearend.deduction.domain.DeductionPolicy;
import com.example.yearend.deduction.domain.DeductionType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class DeductionPolicyRegistry {

    private final Map<DeductionType, DeductionPolicy> policies = new EnumMap<>(DeductionType.class);

    public DeductionPolicyRegistry(List<DeductionPolicy> policies) {
        for (DeductionPolicy policy : policies) {
            this.policies.put(policy.supports(), policy);
        }
    }

    public DeductionPolicy get(DeductionType deductionType) {
        DeductionPolicy policy = policies.get(deductionType);
        if (policy == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_DEDUCTION_TYPE);
        }
        return policy;
    }
}
