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
  { value: "CREDIT_CARD", label: "카드사용액" }
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

export function getChecklistRequirementMeta(requiredYn) {
  return requiredYn
    ? { label: "필수 서류", badgeClass: "bg-rose-100 text-rose-700" }
    : { label: "선택 서류", badgeClass: "bg-slate-100 text-slate-600" };
}

export function getChecklistSubmissionMeta(submittedYn) {
  return submittedYn
    ? { label: "자료 제출됨", badgeClass: "bg-sky-100 text-sky-700" }
    : { label: "자료 미제출", badgeClass: "bg-slate-100 text-slate-600" };
}

export function getChecklistReviewMeta(status) {
  return {
    label: getReviewStatusLabel(status),
    badgeClass: getStatusBadgeClass(status)
  };
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

function parseAttributes(item) {
  if (!item?.attributesJsonb) return {};

  try {
    return JSON.parse(item.attributesJsonb);
  } catch {
    return {};
  }
}

function isEmploymentIncome(item) {
  if (parseAttributes(item).entryKind === "OTHER_INCOME") {
    return false;
  }
  return item?.incomeType === "SALARY" || item?.incomeType === "BONUS";
}

function resolveTaxableIncomeAmount(item) {
  const grossAmount = Number(item?.grossAmount) || 0;
  const nonTaxableAmount = Number(item?.nonTaxableAmount) || 0;
  if (grossAmount > 0 || nonTaxableAmount > 0) {
    return Math.max(grossAmount - nonTaxableAmount, 0);
  }
  return Number(item?.taxableAmount) || 0;
}

export function calculateEarnedIncomeDeduction(taxableSalaryAmount) {
  const amount = Math.max(Number(taxableSalaryAmount) || 0, 0);
  let deductionAmount = 0;

  if (amount <= 5_000_000) {
    deductionAmount = Math.round(amount * 0.7);
  } else if (amount <= 15_000_000) {
    deductionAmount = 3_500_000 + Math.round((amount - 5_000_000) * 0.4);
  } else if (amount <= 45_000_000) {
    deductionAmount = 7_500_000 + Math.round((amount - 15_000_000) * 0.15);
  } else if (amount <= 100_000_000) {
    deductionAmount = 12_000_000 + Math.round((amount - 45_000_000) * 0.05);
  } else {
    deductionAmount = 14_750_000 + Math.round((amount - 100_000_000) * 0.02);
  }

  return Math.min(deductionAmount, 20_000_000);
}

function calculateEstimatedTax(taxBaseAmount) {
  if (taxBaseAmount <= 14_000_000) {
    return Math.round(taxBaseAmount * 0.06);
  }
  return Math.round(taxBaseAmount * 0.15);
}

export function calculateFinancialSummary(incomeItems, deductionItems) {
  const totalGrossIncome = sumAmounts(incomeItems, "grossAmount");
  const totalTaxableIncome = sumAmounts(incomeItems, "taxableAmount");
  const totalWithheldTax = sumAmounts(incomeItems, "withheldTaxAmount");
  const totalNonTaxableIncome = sumAmounts(incomeItems, "nonTaxableAmount");
  const employmentIncomeItems = (Array.isArray(incomeItems) ? incomeItems : []).filter(isEmploymentIncome);
  const otherIncomeItems = (Array.isArray(incomeItems) ? incomeItems : []).filter((item) => !isEmploymentIncome(item));
  const totalGrossSalaryAmount = sumAmounts(employmentIncomeItems, "grossAmount");
  const totalNonTaxableSalaryAmount = sumAmounts(employmentIncomeItems, "nonTaxableAmount");
  const taxableSalaryAmount = employmentIncomeItems.reduce(
    (total, item) => total + resolveTaxableIncomeAmount(item),
    0
  );
  const otherTaxableIncomeAmount = otherIncomeItems.reduce(
    (total, item) => total + resolveTaxableIncomeAmount(item),
    0
  );
  const earnedIncomeDeductionAmount = calculateEarnedIncomeDeduction(taxableSalaryAmount);
  const earnedIncomeAmount = Math.max(taxableSalaryAmount - earnedIncomeDeductionAmount, 0);
  const totalDeduction = sumAmounts(
    (Array.isArray(deductionItems) ? deductionItems : []).filter(isDeductionIncludedInCalculation),
    "amount"
  );
  const estimatedTaxBase = Math.max(earnedIncomeAmount + otherTaxableIncomeAmount - totalDeduction, 0);
  const estimatedTax = calculateEstimatedTax(estimatedTaxBase);
  const estimatedRefund = totalWithheldTax - estimatedTax;

  return {
    totalGrossIncome,
    totalTaxableIncome,
    totalWithheldTax,
    totalNonTaxableIncome,
    totalGrossSalaryAmount,
    totalNonTaxableSalaryAmount,
    taxableSalaryAmount,
    otherTaxableIncomeAmount,
    earnedIncomeDeductionAmount,
    earnedIncomeAmount,
    totalDeduction,
    estimatedTaxBase,
    estimatedTax,
    estimatedRefund
  };
}
