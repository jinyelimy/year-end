package com.example.yearend.common.api;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ErrorResponse {

    private final String code;
    private final String message;
    private final List<FieldErrorDetail> fieldErrors;
}
