package com.example.yearend.taxsession.application;

import com.example.yearend.common.exception.BusinessException;
import com.example.yearend.common.exception.ErrorCode;
import com.example.yearend.deduction.application.RuleVersionNormalizer;
import com.example.yearend.taxsession.api.TaxSessionDtos;
import com.example.yearend.taxsession.domain.FilingType;
import com.example.yearend.taxsession.domain.SessionStatus;
import com.example.yearend.taxsession.domain.TaxSession;
import com.example.yearend.taxsession.infrastructure.TaxSessionRepository;
import com.example.yearend.user.application.UserService;
import com.example.yearend.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaxSessionService {

    private final TaxSessionRepository taxSessionRepository;
    private final UserService userService;
    private final RuleVersionNormalizer ruleVersionNormalizer;

    @Transactional
    public TaxSessionDtos.TaxSessionResponse create(String email, TaxSessionDtos.CreateTaxSessionRequest request) {
        User user = userService.getByEmail(email);

        TaxSession session = new TaxSession();
        session.setUser(user);
        session.setTaxYear(request.taxYear());
        session.setFilingType(Objects.requireNonNullElse(request.filingType(), FilingType.SALARY_WORKER));
        session.setRuleVersion(ruleVersionNormalizer.normalize(request.taxYear(), request.ruleVersion()));
        session.setSessionStatus(SessionStatus.DRAFT);
        session.setBasicInfoJsonb("{}");

        taxSessionRepository.save(session);
        return toResponse(session);
    }

    @Transactional(readOnly = true)
    public TaxSessionDtos.TaxSessionResponse get(String email, UUID sessionId) {
        return toResponse(getOwnedSession(email, sessionId));
    }

    @Transactional(readOnly = true)
    public List<TaxSessionDtos.TaxSessionResponse> list(String email) {
        User user = userService.getByEmail(email);
        return taxSessionRepository.findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(user.getId())
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public TaxSessionDtos.TaxSessionResponse updateBasicInfo(
        String email,
        UUID sessionId,
        TaxSessionDtos.UpdateBasicInfoRequest request
    ) {
        TaxSession session = getOwnedSession(email, sessionId);
        session.setBasicInfoJsonb(request.basicInfoJsonb());
        session.setMemo(request.memo());
        return toResponse(session);
    }

    @Transactional
    public TaxSessionDtos.TaxSessionResponse submit(String email, UUID sessionId) {
        TaxSession session = getOwnedSession(email, sessionId);
        session.setSessionStatus(SessionStatus.SUBMITTED);
        session.setSubmittedAt(OffsetDateTime.now());
        return toResponse(session);
    }

    @Transactional(readOnly = true)
    public TaxSession getOwnedSession(String email, UUID sessionId) {
        User user = userService.getByEmail(email);
        return taxSessionRepository.findByIdAndUserIdAndDeletedAtIsNull(sessionId, user.getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public TaxSession getSession(UUID sessionId) {
        return taxSessionRepository.findByIdAndDeletedAtIsNull(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
    }

    @Transactional
    public void updateStatus(TaxSession session, SessionStatus status) {
        session.setSessionStatus(status);
    }

    public TaxSessionDtos.TaxSessionResponse toResponse(TaxSession session) {
        return new TaxSessionDtos.TaxSessionResponse(
            session.getId(),
            session.getTaxYear(),
            session.getSessionStatus(),
            session.getFilingType(),
            ruleVersionNormalizer.normalize(session.getTaxYear(), session.getRuleVersion()),
            session.getBasicInfoJsonb(),
            session.getMemo(),
            session.getSubmittedAt()
        );
    }
}
