package com.example.yearend.taxsession.application;

import com.example.yearend.deduction.application.RuleVersionNormalizer;
import com.example.yearend.taxsession.api.TaxSessionDtos;
import com.example.yearend.taxsession.domain.FilingType;
import com.example.yearend.taxsession.domain.TaxSession;
import com.example.yearend.taxsession.infrastructure.TaxSessionRepository;
import com.example.yearend.user.application.UserService;
import com.example.yearend.user.domain.User;
import com.example.yearend.user.domain.UserRole;
import com.example.yearend.user.domain.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxSessionServiceTest {

    @Mock
    private TaxSessionRepository taxSessionRepository;

    @Mock
    private UserService userService;

    private TaxSessionService taxSessionService;

    @BeforeEach
    void setUp() {
        taxSessionService = new TaxSessionService(
            taxSessionRepository,
            userService,
            new RuleVersionNormalizer()
        );
    }

    @Test
    @DisplayName("normalizes a legacy rule version before saving a tax session")
    void createNormalizesRuleVersion() {
        User user = user();
        when(userService.getByEmail("tester@example.com")).thenReturn(user);
        when(taxSessionRepository.save(any(TaxSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaxSessionDtos.TaxSessionResponse response = taxSessionService.create(
            "tester@example.com",
            new TaxSessionDtos.CreateTaxSessionRequest(2025, FilingType.SALARY_WORKER, "rule-2025.1")
        );

        ArgumentCaptor<TaxSession> captor = ArgumentCaptor.forClass(TaxSession.class);
        verify(taxSessionRepository).save(captor.capture());

        assertThat(captor.getValue().getRuleVersion()).isEqualTo("2025.01");
        assertThat(response.ruleVersion()).isEqualTo("2025.01");
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("tester@example.com");
        user.setPasswordHash("hash");
        user.setName("Tester");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
