package com.example.yearend.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_400", "Invalid request."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "COMMON_422", "Validation failed."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_401", "Authentication is required."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_403", "You do not have permission to access this resource."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404", "User could not be found."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "SESSION_404", "Tax session could not be found."),
    DEPENDENT_NOT_FOUND(HttpStatus.NOT_FOUND, "DEPENDENT_404", "Dependent could not be found."),
    INCOME_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "INCOME_404", "Income item could not be found."),
    DEDUCTION_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "DEDUCTION_404", "Deduction item could not be found."),
    CALCULATION_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "RESULT_404", "Calculation result could not be found."),
    CHECKLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "DOC_404", "Document checklist could not be found."),
    RULE_SET_NOT_FOUND(HttpStatus.NOT_FOUND, "RULESET_404", "Rule set could not be found."),
    PUBLISHED_RULE_SET_NOT_FOUND(HttpStatus.NOT_FOUND, "RULESET_404_1", "A published rule set could not be found."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "AUTH_409", "Email is already in use."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_401_1", "Email or password is incorrect."),
    UNSUPPORTED_DEDUCTION_TYPE(HttpStatus.BAD_REQUEST, "RULE_400", "Unsupported deduction type."),
    ADMIN_REVIEW_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "ADMIN_400", "This checklist cannot be reviewed in the current state."),
    RULE_SET_REVIEW_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "RULESET_400", "This rule set cannot be reviewed in the current state."),
    RULE_SET_PUBLISH_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "RULESET_400_1", "This rule set cannot be published in the current state.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
