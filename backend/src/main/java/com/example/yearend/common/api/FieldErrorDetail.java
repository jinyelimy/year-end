package com.example.yearend.common.api;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FieldErrorDetail {

    private final String field;
    private final String reason;
}
