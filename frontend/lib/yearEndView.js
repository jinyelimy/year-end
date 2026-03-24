"use client";

import { isDeductionIncludedInCalculation } from "@/lib/deductionImport";

export const INCOME_TYPE_OPTIONS = [
  { value: "SALARY", label: "급여" },
  { value: "BONUS", label: "상여" },
  { value: "OTHER_INCOME", label: "기타소득" }
];

export const DEDUCTION_TYPE_OPTIONS = [
  { value: "MEDICAL_EXPENSE", label: "의료비" },
  { value: "EDUCATION_EXPENSE", label: "교육비" },
  { value: "INSURANCE", label: "보험료" },
  { value: "DONATION", label: "기부금" },
  { value: "CREDIT_CARD", label: "신용카드" }
];

export const EVIDENCE_STATUS_OPTIONS = [
  { value: "PENDING", label: "확인 필요" },
  { value: "SUBMITTED", label: "제출됨" },
  { value: "CONFIRMED", label: "확인 완료" }
];

const DOCUMENT_TYPE_LABELS = {
  MEDICAL_RECEIPT: "의료비 영수증",
  EDUCATION_RECEIPT: "교육비 영수증",
  DONATION_RECEIPT: "기부금 영수증",
  INSURANCE_STATEMENT: "보험료 납입 증명"
};

const REVIEW_STATUS_LABELS = {
  PENDING: "검토 대기",
  APPROVED: "확인됨",
  REJECTED: "보완 필요"
};

const SESSION_STATUS_LABELS = {
  DRAFT: "작성 중",
  CALCULATED: "계산 완료",
  SUBMITTED: "제출 완료",
  REVIEWED: "검토 완료",
  REJECTED: "보완 필요"
};

export function formatCurrency(value) {
  return `${new Intl.NumberFormat("ko-KR").format(Number(value) || 0)}원`;
}

export function formatDate(value) {
  if (!value) {
    return "-";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "long",
    day: "numeric"
  }).format(date);
}

export function getIncomeTypeLabel(type) {
  return INCOME_TYPE_OPTIONS.find((option) => option.value === type)?.label || type || "-";
}

export function getDeductionTypeLabel(type) {
  return DEDUCTION_TYPE_OPTIONS.find((option) => option.value === type)?.label || type || "-";
}

export function getEvidenceStatusLabel(status) {
  return EVIDENCE_STATUS_OPTIONS.find((option) => option.value === status)?.label || status || "-";
}

export function getDocumentTypeLabel(type) {
  return DOCUMENT_TYPE_LABELS[type] || type || "-";
}

export function getReviewStatusLabel(status) {
  return REVIEW_STATUS_LABELS[status] || status || "-";
}

export function getSessionStatusText(status) {
  return SESSION_STATUS_LABELS[status] || status || "-";
}

export function getStatusBadgeClass(status) {
  switch (status) {
    case "REVIEWED":
    case "APPROVED":
    case "CONFIRMED":
    case "SUBMITTED":
      return "bg-emerald-100 text-emerald-700";
    case "REJECTED":
      return "bg-red-100 text-red-700";
    case "CALCULATED":
      return "bg-blue-100 text-blue-700";
    default:
      return "bg-amber-100 text-amber-700";
  }
}

export function sumAmounts(items, key) {
  return (Array.isArray(items) ? items : []).reduce((total, item) => total + (Number(item?.[key]) || 0), 0);
}

export function calculateFinancialSummary(incomeItems, deductionItems) {
  const totalGrossIncome = sumAmounts(incomeItems, "grossAmount");
  const totalTaxableIncome = sumAmounts(incomeItems, "taxableAmount");
  const totalWithheldTax = sumAmounts(incomeItems, "withheldTaxAmount");
  const totalNonTaxableIncome = sumAmounts(incomeItems, "nonTaxableAmount");
  const totalDeduction = sumAmounts(
    (Array.isArray(deductionItems) ? deductionItems : []).filter(isDeductionIncludedInCalculation),
    "amount"
  );
  const estimatedTaxBase = Math.max(totalTaxableIncome - totalDeduction, 0);
  const estimatedTax = Math.round(estimatedTaxBase * 0.06);
  const estimatedRefund = totalWithheldTax - estimatedTax;

  return {
    totalGrossIncome,
    totalTaxableIncome,
    totalWithheldTax,
    totalNonTaxableIncome,
    totalDeduction,
    estimatedTaxBase,
    estimatedTax,
    estimatedRefund
  };
}
