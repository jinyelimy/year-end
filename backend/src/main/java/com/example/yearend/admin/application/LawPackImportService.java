package com.example.yearend.admin.application;

import com.example.yearend.admin.api.AdminDtos;
import com.example.yearend.common.exception.BusinessException;
import com.example.yearend.common.exception.ErrorCode;
import com.example.yearend.deduction.application.RuleVersionNormalizer;
import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleCategory;
import com.example.yearend.deduction.domain.RuleSetStatus;
import com.example.yearend.deduction.domain.TaxRuleSet;
import com.example.yearend.deduction.infrastructure.DeductionRuleRepository;
import com.example.yearend.deduction.infrastructure.TaxRuleSetRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Imports a harness-produced normalized-rule-pack.json into the backend database.
 *
 * Imported rule sets always land in DRAFT state. The existing human-review/publish
 * flow (AdminRuleSetService.reviewRuleSet + publishRuleSet) is the only path that
 * can transition a set to PUBLISHED. This preserves the "자동 수집은 허용하지만
 * 자동 publish 는 하지 않는다" principle from the harness command list.
 *
 * Conflict policy for (taxYear, ruleVersion):
 * - no existing set → create new DRAFT
 * - existing set in DRAFT → replace (delete old rules, re-import into same ruleset row)
 * - existing set in any other status → reject with 409
 */
@Service
@RequiredArgsConstructor
public class LawPackImportService {

    private final TaxRuleSetRepository taxRuleSetRepository;
    private final DeductionRuleRepository deductionRuleRepository;
    private final RuleVersionNormalizer ruleVersionNormalizer;
    private final ObjectMapper objectMapper;

    @Transactional
    public AdminDtos.ImportRuleSetResponse importLawPack(MultipartFile file) {
        JsonNode root = parse(file);

        Integer taxYear = requireInt(root, "taxYear");
        String ruleVersion = requireText(root, "ruleVersion");
        JsonNode rulesNode = root.get("rules");
        if (rulesNode == null || !rulesNode.isArray() || rulesNode.isEmpty()) {
            throw new BusinessException(
                ErrorCode.RULE_SET_IMPORT_INVALID,
                "rules[] must be a non-empty array."
            );
        }

        String canonicalVersion = ruleVersionNormalizer.normalize(taxYear, ruleVersion);
        List<ParsedRule> parsedRules = validateAllRules(rulesNode);

        TaxRuleSet ruleSet = resolveOrCreateDraftRuleSet(taxYear, canonicalVersion);
        String canonicalPackPath = optionalText(root, "ruleSetId");
        if (canonicalPackPath != null && !canonicalPackPath.isBlank()) {
            ruleSet.setCanonicalPackPath(canonicalPackPath);
        }
        ruleSet = taxRuleSetRepository.save(ruleSet);

        int deletedCount = deleteExistingRules(ruleSet.getId());

        List<DeductionRule> importedRules = new ArrayList<>();
        for (ParsedRule parsed : parsedRules) {
            importedRules.add(parsed.toEntity(taxYear, ruleSet));
        }
        deductionRuleRepository.saveAll(importedRules);

        return new AdminDtos.ImportRuleSetResponse(
            ruleSet.getId(),
            ruleSet.getTaxYear(),
            ruleSet.getRuleVersion(),
            ruleSet.getStatus(),
            importedRules.size(),
            deletedCount
        );
    }

    private List<ParsedRule> validateAllRules(JsonNode rulesNode) {
        List<ParsedRule> parsed = new ArrayList<>();
        for (int i = 0; i < rulesNode.size(); i++) {
            parsed.add(parseRule(rulesNode.get(i), i));
        }
        return parsed;
    }

    private ParsedRule parseRule(JsonNode ruleNode, int index) {
        String ruleCode = requireText(ruleNode, "ruleCode", index);
        String deductionTypeName = requireText(ruleNode, "deductionType", index);
        String ruleCategoryName = requireText(ruleNode, "ruleCategory", index);
        String effectiveFromText = requireText(ruleNode, "effectiveFrom", index);

        DeductionType deductionType = parseEnum(DeductionType.class, deductionTypeName, "deductionType", index);
        RuleCategory ruleCategory = parseEnum(RuleCategory.class, ruleCategoryName, "ruleCategory", index);
        LocalDate effectiveFrom = parseDate(effectiveFromText, "effectiveFrom", index);
        LocalDate effectiveTo = optionalDate(ruleNode, "effectiveTo", index);

        JsonNode parameters = ruleNode.get("parameters");
        String parameterJson = parameters == null || parameters.isNull() ? "{}" : parameters.toString();
        String description = optionalText(ruleNode, "subType");

        return new ParsedRule(
            ruleCode,
            deductionType,
            ruleCategory,
            parameterJson,
            description,
            effectiveFrom,
            effectiveTo
        );
    }

    private record ParsedRule(
        String ruleCode,
        DeductionType deductionType,
        RuleCategory ruleCategory,
        String parameterJson,
        String description,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
    ) {
        DeductionRule toEntity(Integer taxYear, TaxRuleSet ruleSet) {
            DeductionRule rule = new DeductionRule();
            rule.setTaxYear(taxYear);
            rule.setTaxRuleSet(ruleSet);
            rule.setDeductionType(deductionType);
            rule.setRuleCode(ruleCode);
            rule.setRuleName(ruleCode);
            rule.setRuleVersion(1);
            rule.setRuleCategory(ruleCategory);
            rule.setParameterJsonb(parameterJson);
            rule.setDescription(description);
            rule.setEffectiveFrom(effectiveFrom);
            rule.setEffectiveTo(effectiveTo);
            rule.setActive(false);
            return rule;
        }
    }

    private JsonNode parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.RULE_SET_IMPORT_INVALID, "Uploaded file is empty.");
        }
        try {
            return objectMapper.readTree(file.getBytes());
        } catch (IOException exception) {
            throw new BusinessException(
                ErrorCode.RULE_SET_IMPORT_INVALID,
                "Failed to parse law pack JSON: " + exception.getMessage()
            );
        }
    }

    private TaxRuleSet resolveOrCreateDraftRuleSet(Integer taxYear, String canonicalVersion) {
        Optional<TaxRuleSet> existing = taxRuleSetRepository
            .findFirstByTaxYearAndRuleVersion(taxYear, canonicalVersion);
        if (existing.isEmpty()) {
            TaxRuleSet fresh = new TaxRuleSet();
            fresh.setTaxYear(taxYear);
            fresh.setRuleVersion(canonicalVersion);
            fresh.setStatus(RuleSetStatus.DRAFT);
            return fresh;
        }

        TaxRuleSet current = existing.get();
        if (current.getStatus() != RuleSetStatus.DRAFT) {
            throw new BusinessException(
                ErrorCode.RULE_SET_IMPORT_CONFLICT,
                "Rule set " + taxYear + "/" + canonicalVersion + " already exists with status "
                    + current.getStatus() + "; only DRAFT sets can be re-imported."
            );
        }
        return current;
    }

    private int deleteExistingRules(java.util.UUID ruleSetId) {
        List<DeductionRule> existing = deductionRuleRepository.findAllByTaxRuleSetId(ruleSetId);
        if (existing.isEmpty()) {
            return 0;
        }
        deductionRuleRepository.deleteAll(existing);
        return existing.size();
    }

    private Integer requireInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToInt()) {
            throw new BusinessException(
                ErrorCode.RULE_SET_IMPORT_INVALID,
                "Missing or non-integer field: " + field
            );
        }
        return value.asInt();
    }

    private String requireText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new BusinessException(
                ErrorCode.RULE_SET_IMPORT_INVALID,
                "Missing or blank string field: " + field
            );
        }
        return value.asText();
    }

    private String requireText(JsonNode node, String field, int index) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new BusinessException(
                ErrorCode.RULE_SET_IMPORT_INVALID,
                "rules[" + index + "]." + field + " is missing or blank."
            );
        }
        return value.asText();
    }

    private String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isTextual() ? null : value.asText();
    }

    private LocalDate optionalDate(JsonNode node, String field, int index) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            return null;
        }
        return parseDate(value.asText(), field, index);
    }

    private LocalDate parseDate(String text, String field, int index) {
        try {
            return LocalDate.parse(text);
        } catch (RuntimeException exception) {
            throw new BusinessException(
                ErrorCode.RULE_SET_IMPORT_INVALID,
                "rules[" + index + "]." + field + " is not a valid ISO date: " + text
            );
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field, int index) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                ErrorCode.RULE_SET_IMPORT_INVALID,
                "rules[" + index + "]." + field + " is not a recognized " + type.getSimpleName() + ": " + value
            );
        }
    }
}
