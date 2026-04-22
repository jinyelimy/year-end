"use client";

export const STORAGE_KEYS = {
  accessToken: "accessToken",
  refreshToken: "refreshToken",
  expiresInSeconds: "expiresInSeconds",
  accessTokenExpiresAt: "accessTokenExpiresAt",
  userProfile: "userProfile",
  currentSessionId: "currentSessionId",
  currentSession: "currentSession"
};

function isBrowser() {
  return typeof window !== "undefined";
}

function readStorage(key) {
  if (!isBrowser()) return null;
  return window.localStorage.getItem(key);
}

function writeStorage(key, value) {
  if (!isBrowser()) return;
  window.localStorage.setItem(key, value);
}

function removeStorage(key) {
  if (!isBrowser()) return;
  window.localStorage.removeItem(key);
}

export function clearAuth() {
  Object.values(STORAGE_KEYS).forEach(removeStorage);
}

export function isAccessTokenExpired() {
  const expiresAt = readStorage(STORAGE_KEYS.accessTokenExpiresAt);
  if (!expiresAt) return false;
  const expiresAtTime = Number(expiresAt);
  if (!Number.isFinite(expiresAtTime)) return false;
  return Date.now() >= expiresAtTime;
}

export function getAccessToken() {
  if (isAccessTokenExpired()) {
    clearAuth();
    return null;
  }
  return readStorage(STORAGE_KEYS.accessToken);
}

export function persistAuthTokens(data) {
  writeStorage(STORAGE_KEYS.accessToken, data.accessToken);
  writeStorage(STORAGE_KEYS.refreshToken, data.refreshToken);
  writeStorage(STORAGE_KEYS.expiresInSeconds, String(data.expiresInSeconds));
  writeStorage(
    STORAGE_KEYS.accessTokenExpiresAt,
    String(Date.now() + (Number(data.expiresInSeconds) || 0) * 1000)
  );
}

export function saveUserProfile(profile) {
  writeStorage(STORAGE_KEYS.userProfile, JSON.stringify(profile));
}

export function saveCurrentSession(session) {
  writeStorage(STORAGE_KEYS.currentSessionId, session.id);
  writeStorage(STORAGE_KEYS.currentSession, JSON.stringify(session));
}

export function getCurrentSessionId() {
  return readStorage(STORAGE_KEYS.currentSessionId);
}

function getDefaultTaxYear() {
  return new Date().getFullYear() - 1;
}

function getDefaultRuleVersion(taxYear) {
  return `rule-${taxYear}.1`;
}

export async function request(path, options = {}, config = {}) {
  const headers = new Headers(options.headers || {});
  const isFormData = typeof FormData !== "undefined" && options.body instanceof FormData;
  const isJson = options.body && typeof options.body !== "string" && !isFormData;

  if (isJson) {
    headers.set("Content-Type", "application/json");
  }

  if (config.auth !== false) {
    const token = getAccessToken();
    if (!token) {
      const error = new Error("로그인이 필요합니다.");
      error.status = 401;
      throw error;
    }
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(path, {
    ...options,
    headers,
    body: isJson ? JSON.stringify(options.body) : options.body,
    cache: "no-store"
  });

  const payload = await response.json().catch(() => ({
    success: false,
    error: { message: "서버 응답을 처리하지 못했습니다." }
  }));

  if (!response.ok || !payload.success) {
    const error = new Error(payload.error?.message || "요청에 실패했습니다.");
    error.status = response.status;
    error.payload = payload;

    if (response.status === 401 && config.auth !== false) {
      clearAuth();
    }

    throw error;
  }

  return payload.data;
}

export function parseBasicInfo(session) {
  if (!session?.basicInfoJsonb) return {};

  try {
    return JSON.parse(session.basicInfoJsonb);
  } catch {
    return {};
  }
}

export function hasBasicInfo(session) {
  const basicInfo = parseBasicInfo(session);
  return Boolean(basicInfo.fullName || basicInfo.address || basicInfo.reportingType);
}

export function hasDependentsConfirmed(session) {
  const basicInfo = parseBasicInfo(session);
  return basicInfo.dependentsConfirmed === true;
}

export function hasIncomeConfirmed(session) {
  const basicInfo = parseBasicInfo(session);
  return basicInfo.incomeConfirmed === true;
}

export function hasDeductionsConfirmed(session) {
  const basicInfo = parseBasicInfo(session);
  return basicInfo.deductionsConfirmed === true;
}

export function isSubmissionComplete(session) {
  return ["SUBMITTED", "REVIEWED", "REJECTED"].includes(session?.sessionStatus);
}

export function isDocumentsStageComplete(checklists = []) {
  if (!Array.isArray(checklists) || checklists.length === 0) {
    return true;
  }

  return checklists.every(
    (item) => item?.requiredYn !== true || item?.submittedYn || item?.reviewStatus === "APPROVED"
  );
}

export function isProfileSectionComplete(session) {
  return hasBasicInfo(session) && hasDependentsConfirmed(session);
}

export function resolveNextStep(session, checklists = []) {
  if (!session) {
    return { href: "/basic-info", label: "기본정보 입력 시작하기" };
  }

  if (isSubmissionComplete(session)) {
    return { href: "/submit-status", label: "제출 상태 확인하기" };
  }

  if (session.sessionStatus === "CALCULATED") {
    return { href: "/results", label: "결과 확인하기" };
  }

  if (!hasBasicInfo(session)) {
    return { href: "/basic-info", label: "기본정보 입력하기" };
  }

  if (!hasDependentsConfirmed(session)) {
    return { href: "/dependents", label: "1단계 마무리하기" };
  }

  if (!hasIncomeConfirmed(session)) {
    return { href: "/income", label: "2단계 진행하기" };
  }

  if (!hasDeductionsConfirmed(session)) {
    return { href: "/import-data", label: "3단계 진행하기" };
  }

  if (!isDocumentsStageComplete(checklists)) {
    return { href: "/evidence-docs", label: "증빙 서류 점검하기" };
  }

  return { href: "/results", label: "5단계 진행하기" };
}

export function getCompletedStageCount(session, checklists = []) {
  if (!session) return 0;

  let completedStageCount = 0;

  if (!hasDependentsConfirmed(session)) return completedStageCount;
  completedStageCount += 1;

  if (!hasIncomeConfirmed(session)) return completedStageCount;
  completedStageCount += 1;

  if (!hasDeductionsConfirmed(session)) return completedStageCount;
  completedStageCount += 1;

  if (!isDocumentsStageComplete(checklists)) return completedStageCount;
  completedStageCount += 1;

  if (isSubmissionComplete(session)) {
    completedStageCount += 1;
  }

  return completedStageCount;
}

export function resolveProgress(session, checklists = []) {
  return getCompletedStageCount(session, checklists) * 20;
}

export function getSessionStatusLabel(session, checklists = []) {
  if (!session) return "세션 없음";

  if (session.sessionStatus === "DRAFT") {
    const completedStageCount = getCompletedStageCount(session, checklists);
    if (completedStageCount >= 4) return "4단계 완료";
    if (completedStageCount >= 3) return "3단계 완료";
    if (completedStageCount >= 2) return "2단계 완료";
    if (completedStageCount >= 1) return "1단계 완료";
    return "1단계 진행 중";
  }

  const labels = {
    CALCULATED: "계산 완료",
    SUBMITTED: "제출 완료",
    REVIEWED: "검토 완료",
    REJECTED: "보완 필요"
  };

  return labels[session.sessionStatus] || session.sessionStatus;
}

export function maskSsn(value) {
  const digits = String(value || "").replace(/\D/g, "");
  if (!digits) return "";
  return `***-**-${digits.slice(-4)}`;
}

export async function getMe() {
  return request("/api/v1/users/me");
}

export async function listTaxSessions() {
  return request("/api/v1/tax-sessions");
}

export async function getTaxSession(sessionId) {
  return request(`/api/v1/tax-sessions/${sessionId}`);
}

export async function createTaxSession(payload) {
  return request("/api/v1/tax-sessions", { method: "POST", body: payload });
}

export async function updateBasicInfo(sessionId, payload) {
  return request(`/api/v1/tax-sessions/${sessionId}/basic-info`, {
    method: "PATCH",
    body: payload
  });
}

export async function listDependents(sessionId) {
  return request(`/api/v1/tax-sessions/${sessionId}/dependents`);
}

export async function createDependent(sessionId, payload) {
  return request(`/api/v1/tax-sessions/${sessionId}/dependents`, {
    method: "POST",
    body: payload
  });
}

export async function updateDependent(sessionId, dependentId, payload) {
  return request(`/api/v1/tax-sessions/${sessionId}/dependents/${dependentId}`, {
    method: "PUT",
    body: payload
  });
}

export async function deleteDependent(sessionId, dependentId) {
  return request(`/api/v1/tax-sessions/${sessionId}/dependents/${dependentId}`, {
    method: "DELETE"
  });
}

export async function listIncomeItems(sessionId) {
  return request(`/api/v1/tax-sessions/${sessionId}/income-items`);
}

export async function createIncomeItem(sessionId, payload) {
  return request(`/api/v1/tax-sessions/${sessionId}/income-items`, {
    method: "POST",
    body: payload
  });
}

export async function updateIncomeItem(sessionId, incomeItemId, payload) {
  return request(`/api/v1/tax-sessions/${sessionId}/income-items/${incomeItemId}`, {
    method: "PUT",
    body: payload
  });
}

export async function deleteIncomeItem(sessionId, incomeItemId) {
  return request(`/api/v1/tax-sessions/${sessionId}/income-items/${incomeItemId}`, {
    method: "DELETE"
  });
}

export async function listDeductionItems(sessionId) {
  return request(`/api/v1/tax-sessions/${sessionId}/deduction-items`);
}

export async function createDeductionItem(sessionId, payload) {
  return request(`/api/v1/tax-sessions/${sessionId}/deduction-items`, {
    method: "POST",
    body: payload
  });
}

export async function updateDeductionItem(sessionId, deductionItemId, payload) {
  return request(`/api/v1/tax-sessions/${sessionId}/deduction-items/${deductionItemId}`, {
    method: "PUT",
    body: payload
  });
}

export async function deleteDeductionItem(sessionId, deductionItemId) {
  return request(`/api/v1/tax-sessions/${sessionId}/deduction-items/${deductionItemId}`, {
    method: "DELETE"
  });
}

export async function importHometaxPdf(sessionId, file) {
  const formData = new FormData();
  formData.append("file", file);

  return request(`/api/v1/tax-sessions/${sessionId}/deduction-items/imports/hometax`, {
    method: "POST",
    body: formData
  });
}

export async function runSimulation(sessionId) {
  return request(`/api/v1/tax-sessions/${sessionId}/simulation`, {
    method: "POST"
  });
}

export async function listDocumentChecklists(sessionId) {
  return request(`/api/v1/tax-sessions/${sessionId}/document-checklists`);
}

export async function submitTaxSession(sessionId) {
  return request(`/api/v1/tax-sessions/${sessionId}/submit`, {
    method: "POST"
  });
}

export async function getSocialAuthorizeUrl(provider, redirectUri, state) {
  const query = new URLSearchParams({ redirectUri });
  if (state) {
    query.set("state", state);
  }

  return request(
    `/api/v1/auth/oauth/${provider}/authorize-url?${query.toString()}`,
    {},
    { auth: false }
  );
}

export async function exchangeSocialCode(provider, payload) {
  return request(
    `/api/v1/auth/oauth/${provider}/exchange`,
    {
      method: "POST",
      body: payload
    },
    { auth: false }
  );
}

export async function ensureCurrentSession() {
  const storedSessionId = getCurrentSessionId();

  if (storedSessionId) {
    try {
      const storedSession = await getTaxSession(storedSessionId);
      saveCurrentSession(storedSession);
      return storedSession;
    } catch (error) {
      if (error.status !== 404) throw error;
    }
  }

  const sessions = await listTaxSessions();
  if (Array.isArray(sessions) && sessions.length > 0) {
    saveCurrentSession(sessions[0]);
    return sessions[0];
  }

  const taxYear = getDefaultTaxYear();
  const created = await createTaxSession({
    taxYear,
    filingType: "SALARY_WORKER",
    ruleVersion: getDefaultRuleVersion(taxYear)
  });
  saveCurrentSession(created);
  return created;
}

export async function initializeAuthenticatedContext() {
  const user = await getMe();
  saveUserProfile(user);
  const currentSession = await ensureCurrentSession();
  return { user, currentSession };
}

export async function importLawPack(file) {
  const formData = new FormData();
  formData.append("file", file);
  return request("/api/v1/admin/rule-sets/import", {
    method: "POST",
    body: formData
  });
}
