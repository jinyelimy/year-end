package com.example.yearend.deduction.application;

import com.example.yearend.common.exception.BusinessException;
import com.example.yearend.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class RuleVersionNormalizer {

    public String normalize(int taxYear, String rawRuleVersion) {
        if (rawRuleVersion == null || rawRuleVersion.isBlank()) {
            return defaultVersion(taxYear);
        }

        String candidate = rawRuleVersion.trim();
        if (candidate.regionMatches(true, 0, "rule-", 0, 5)) {
            candidate = candidate.substring(5);
        }

        String[] parts = candidate.split("\\.");
        if (parts.length < 2 || parts.length > 3) {
            throw invalidFormat(rawRuleVersion);
        }

        if (parts[0].length() != 4) {
            throw invalidFormat(rawRuleVersion);
        }

        int versionTaxYear = parsePositive(parts[0], rawRuleVersion);
        if (versionTaxYear != taxYear) {
            throw new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "Rule version tax year must match the session tax year."
            );
        }

        int month = parsePositive(parts[1], rawRuleVersion);
        if (month < 1 || month > 12) {
            throw invalidFormat(rawRuleVersion);
        }

        String canonical = String.format(Locale.ROOT, "%04d.%02d", versionTaxYear, month);
        if (parts.length == 2) {
            return canonical;
        }

        int patch = parsePositive(parts[2], rawRuleVersion);
        if (patch < 1) {
            throw invalidFormat(rawRuleVersion);
        }

        return canonical + "." + patch;
    }

    public String defaultVersion(int taxYear) {
        return String.format(Locale.ROOT, "%04d.01", taxYear);
    }

    private int parsePositive(String token, String rawRuleVersion) {
        if (token == null || token.isBlank()) {
            throw invalidFormat(rawRuleVersion);
        }

        for (int index = 0; index < token.length(); index++) {
            if (!Character.isDigit(token.charAt(index))) {
                throw invalidFormat(rawRuleVersion);
            }
        }

        return Integer.parseInt(token);
    }

    private BusinessException invalidFormat(String rawRuleVersion) {
        return new BusinessException(
            ErrorCode.INVALID_REQUEST,
            "Rule version '" + rawRuleVersion + "' must use YYYY.MM[.patch]."
        );
    }
}
