(function () {
  const STORAGE_KEYS = {
    accessToken: "accessToken",
    refreshToken: "refreshToken",
    expiresInSeconds: "expiresInSeconds",
    userProfile: "userProfile",
    currentSessionId: "currentSessionId",
    currentSession: "currentSession"
  };

  function getAccessToken() {
    return localStorage.getItem(STORAGE_KEYS.accessToken);
  }

  function persistAuthTokens(data) {
    localStorage.setItem(STORAGE_KEYS.accessToken, data.accessToken);
    localStorage.setItem(STORAGE_KEYS.refreshToken, data.refreshToken);
    localStorage.setItem(STORAGE_KEYS.expiresInSeconds, String(data.expiresInSeconds));
  }

  function clearAuth() {
    Object.values(STORAGE_KEYS).forEach((key) => localStorage.removeItem(key));
  }

  function saveUserProfile(profile) {
    localStorage.setItem(STORAGE_KEYS.userProfile, JSON.stringify(profile));
  }

  function getStoredUserProfile() {
    try {
      return JSON.parse(localStorage.getItem(STORAGE_KEYS.userProfile) || "null");
    } catch (error) {
      return null;
    }
  }

  function saveCurrentSession(session) {
    localStorage.setItem(STORAGE_KEYS.currentSessionId, session.id);
    localStorage.setItem(STORAGE_KEYS.currentSession, JSON.stringify(session));
  }

  function getCurrentSessionId() {
    return localStorage.getItem(STORAGE_KEYS.currentSessionId);
  }

  function getStoredCurrentSession() {
    try {
      return JSON.parse(localStorage.getItem(STORAGE_KEYS.currentSession) || "null");
    } catch (error) {
      return null;
    }
  }

  function getDefaultTaxYear() {
    return new Date().getFullYear() - 1;
  }

  function getDefaultRuleVersion(taxYear) {
    return "rule-" + taxYear + ".1";
  }

  async function request(path, options, config) {
    const requestOptions = options || {};
    const requestConfig = config || {};
    const headers = new Headers(requestOptions.headers || {});
    const isJson = requestOptions.body && typeof requestOptions.body !== "string";

    if (isJson) {
      headers.set("Content-Type", "application/json");
    }

    if (requestConfig.auth !== false) {
      const token = getAccessToken();
      if (!token) {
        const error = new Error("로그인이 필요합니다.");
        error.status = 401;
        throw error;
      }
      headers.set("Authorization", "Bearer " + token);
    }

    const response = await fetch(path, {
      ...requestOptions,
      headers,
      body: isJson ? JSON.stringify(requestOptions.body) : requestOptions.body
    });

    const payload = await response.json().catch(function () {
      return {
        success: false,
        error: { message: "서버 응답을 처리하지 못했습니다." }
      };
    });

    if (!response.ok || !payload.success) {
      const error = new Error(payload.error && payload.error.message ? payload.error.message : "요청에 실패했습니다.");
      error.status = response.status;
      error.payload = payload;
      if (response.status === 401 && requestConfig.auth !== false) {
        clearAuth();
      }
      throw error;
    }

    return payload.data;
  }

  async function getMe() {
    return request("/api/v1/users/me");
  }

  async function listTaxSessions() {
    return request("/api/v1/tax-sessions");
  }

  async function getTaxSession(sessionId) {
    return request("/api/v1/tax-sessions/" + sessionId);
  }

  async function createTaxSession(payload) {
    return request("/api/v1/tax-sessions", {
      method: "POST",
      body: payload
    });
  }

  async function updateBasicInfo(sessionId, payload) {
    return request("/api/v1/tax-sessions/" + sessionId + "/basic-info", {
      method: "PATCH",
      body: payload
    });
  }

  async function listDependents(sessionId) {
    return request("/api/v1/tax-sessions/" + sessionId + "/dependents");
  }

  async function createDependent(sessionId, payload) {
    return request("/api/v1/tax-sessions/" + sessionId + "/dependents", {
      method: "POST",
      body: payload
    });
  }

  async function updateDependent(sessionId, dependentId, payload) {
    return request("/api/v1/tax-sessions/" + sessionId + "/dependents/" + dependentId, {
      method: "PUT",
      body: payload
    });
  }

  async function deleteDependent(sessionId, dependentId) {
    return request("/api/v1/tax-sessions/" + sessionId + "/dependents/" + dependentId, {
      method: "DELETE"
    });
  }

  function parseBasicInfo(session) {
    if (!session || !session.basicInfoJsonb) {
      return {};
    }
    try {
      return JSON.parse(session.basicInfoJsonb);
    } catch (error) {
      return {};
    }
  }

  function hasBasicInfo(session) {
    const basicInfo = parseBasicInfo(session);
    return Boolean(basicInfo.fullName || basicInfo.address || basicInfo.reportingType);
  }

  function resolveNextStep(session) {
    if (!session) {
      return { href: "/basic-info.html", label: "세션 생성 후 시작하기" };
    }

    if (session.sessionStatus === "SUBMITTED" || session.sessionStatus === "REVIEWED" || session.sessionStatus === "REJECTED") {
      return { href: "/submit-status.html", label: "제출 상태 확인하기" };
    }

    if (session.sessionStatus === "CALCULATED") {
      return { href: "/results.html", label: "계산 결과 확인하기" };
    }

    if (!hasBasicInfo(session)) {
      return { href: "/basic-info.html", label: "기본 정보 입력하기" };
    }

    return { href: "/dependents.html", label: "부양가족 입력 이어가기" };
  }

  function resolveProgress(session) {
    if (!session) {
      return 10;
    }
    if (session.sessionStatus === "REVIEWED") {
      return 100;
    }
    if (session.sessionStatus === "SUBMITTED") {
      return 90;
    }
    if (session.sessionStatus === "CALCULATED") {
      return 75;
    }
    if (hasBasicInfo(session)) {
      return 40;
    }
    return 20;
  }

  function getSessionStatusLabel(session) {
    if (!session) {
      return "세션 없음";
    }

    const labels = {
      DRAFT: "입력 중",
      CALCULATED: "계산 완료",
      SUBMITTED: "제출 완료",
      REVIEWED: "검토 완료",
      REJECTED: "보완 필요"
    };

    return labels[session.sessionStatus] || session.sessionStatus;
  }

  async function ensureCurrentSession() {
    const storedSessionId = getCurrentSessionId();
    if (storedSessionId) {
      try {
        const storedSession = await getTaxSession(storedSessionId);
        saveCurrentSession(storedSession);
        return storedSession;
      } catch (error) {
        if (error.status !== 404) {
          throw error;
        }
      }
    }

    const sessions = await listTaxSessions();
    if (Array.isArray(sessions) && sessions.length > 0) {
      saveCurrentSession(sessions[0]);
      return sessions[0];
    }

    const taxYear = getDefaultTaxYear();
    const created = await createTaxSession({
      taxYear: taxYear,
      filingType: "SALARY_WORKER",
      ruleVersion: getDefaultRuleVersion(taxYear)
    });
    saveCurrentSession(created);
    return created;
  }

  async function initializeAuthenticatedContext() {
    const user = await getMe();
    saveUserProfile(user);
    const currentSession = await ensureCurrentSession();
    return { user, currentSession };
  }

  function requireAuthOrRedirect() {
    if (!getAccessToken()) {
      window.location.replace("/auth.html");
      return false;
    }
    return true;
  }

  function maskSsn(value) {
    const digits = String(value || "").replace(/\D/g, "");
    if (!digits) {
      return "";
    }
    const lastFour = digits.slice(-4);
    return "***-**-" + lastFour;
  }

  window.YearEndApp = {
    STORAGE_KEYS,
    request,
    getMe,
    listTaxSessions,
    getTaxSession,
    createTaxSession,
    updateBasicInfo,
    listDependents,
    createDependent,
    updateDependent,
    deleteDependent,
    persistAuthTokens,
    clearAuth,
    saveUserProfile,
    getStoredUserProfile,
    saveCurrentSession,
    getStoredCurrentSession,
    getCurrentSessionId,
    initializeAuthenticatedContext,
    requireAuthOrRedirect,
    parseBasicInfo,
    hasBasicInfo,
    resolveNextStep,
    resolveProgress,
    getSessionStatusLabel,
    maskSsn
  };
})();
