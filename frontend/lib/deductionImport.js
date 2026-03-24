const SOURCE_META = {
  HOMETAX: {
    label: "홈택스 PDF",
    badgeClass: "bg-sky-100 text-sky-700"
  },
  MANUAL: {
    label: "직접 입력",
    badgeClass: "bg-slate-100 text-slate-600"
  }
};

const REVIEW_META = {
  APPROVED: {
    label: "자동 반영",
    badgeClass: "bg-emerald-100 text-emerald-700"
  },
  PENDING: {
    label: "확인 필요",
    badgeClass: "bg-amber-100 text-amber-700"
  },
  EXCLUDED: {
    label: "제외됨",
    badgeClass: "bg-slate-200 text-slate-600"
  }
};

const CONFIDENCE_META = {
  HIGH: { label: "높음", badgeClass: "bg-emerald-100 text-emerald-700" },
  MEDIUM: { label: "보통", badgeClass: "bg-amber-100 text-amber-700" },
  LOW: { label: "낮음", badgeClass: "bg-rose-100 text-rose-700" }
};

export function parseDeductionAttributes(item) {
  if (!item?.attributesJsonb) {
    return {};
  }

  try {
    return JSON.parse(item.attributesJsonb);
  } catch {
    return {};
  }
}

export function getDeductionSource(item) {
  const attributes = parseDeductionAttributes(item);
  const sourceType = attributes.sourceType || "MANUAL";
  return {
    type: sourceType,
    ...(SOURCE_META[sourceType] || SOURCE_META.MANUAL)
  };
}

export function isImportedDeduction(item) {
  return getDeductionSource(item).type === "HOMETAX";
}

export function getImportBucket(item) {
  const attributes = parseDeductionAttributes(item);
  if (!isImportedDeduction(item)) {
    return "MANUAL";
  }

  return attributes.importBucket || (attributes.reviewStatus === "PENDING" ? "NEEDS_REVIEW" : "AUTO_APPLIED");
}

export function getImportReviewMeta(item) {
  const attributes = parseDeductionAttributes(item);
  const status = isImportedDeduction(item)
    ? attributes.reviewStatus || "APPROVED"
    : "APPROVED";

  return {
    status,
    ...(REVIEW_META[status] || REVIEW_META.APPROVED)
  };
}

export function getConfidenceMeta(item) {
  const attributes = parseDeductionAttributes(item);
  const level = attributes.confidenceLevel || "HIGH";
  return {
    level,
    ...(CONFIDENCE_META[level] || CONFIDENCE_META.HIGH)
  };
}

export function getImportReviewReason(item) {
  return parseDeductionAttributes(item).reviewReason || "";
}

export function getLatestImportSummary(items = []) {
  const importedItems = items.filter(isImportedDeduction);
  if (importedItems.length === 0) {
    return null;
  }

  const groups = importedItems.reduce((accumulator, item) => {
    const attributes = parseDeductionAttributes(item);
    const batchId = attributes.importBatchId || "unknown";
    const current = accumulator.get(batchId) || {
      batchId,
      fileName: attributes.importFileName || "홈택스 PDF",
      importedAt: attributes.importedAt || null,
      items: []
    };

    current.items.push(item);
    if (!current.importedAt || (attributes.importedAt && attributes.importedAt > current.importedAt)) {
      current.importedAt = attributes.importedAt;
    }

    accumulator.set(batchId, current);
    return accumulator;
  }, new Map());

  return Array.from(groups.values()).sort((left, right) => {
    const leftTime = left.importedAt ? new Date(left.importedAt).getTime() : 0;
    const rightTime = right.importedAt ? new Date(right.importedAt).getTime() : 0;
    return rightTime - leftTime;
  })[0];
}

export function isDeductionIncludedInCalculation(item) {
  if (!isImportedDeduction(item)) {
    return true;
  }

  const reviewStatus = parseDeductionAttributes(item).reviewStatus || "APPROVED";
  return reviewStatus !== "PENDING" && reviewStatus !== "EXCLUDED";
}

export function buildDeductionAttributes(currentAttributes = {}, overrides = {}) {
  const next = {
    ...currentAttributes,
    ...overrides
  };

  if (!next.sourceType) {
    next.sourceType = "MANUAL";
  }

  if (!next.sourceLabel) {
    next.sourceLabel = next.sourceType === "HOMETAX" ? "홈택스 PDF" : "직접 입력";
  }

  if (!next.entryChannel) {
    next.entryChannel = next.sourceType === "HOMETAX" ? "IMPORT_SYNC" : "MANUAL_FORM";
  }

  return next;
}

export function buildSuggestedImportHints(items = []) {
  const includedItems = items.filter(isDeductionIncludedInCalculation);
  const deductionTypes = new Set(includedItems.map((item) => item.deductionType));
  const hints = [];

  if (!deductionTypes.has("CREDIT_CARD")) {
    hints.push({
      id: "credit-card",
      title: "신용카드 사용액 확인",
      description: "홈택스 가져오기 이후에도 카드 사용액은 빠지는 경우가 많습니다. 카드 공제는 deductions 화면에서 직접 확인해 주세요.",
      href: "/deductions"
    });
  }

  if (!deductionTypes.has("DONATION")) {
    hints.push({
      id: "donation",
      title: "기부금 누락 여부 확인",
      description: "종교단체나 지정기부금 내역은 증빙 상태에 따라 빠질 수 있습니다. 없다면 직접 입력으로 보완하세요.",
      href: "/deductions"
    });
  }

  if (!deductionTypes.has("EDUCATION_EXPENSE")) {
    hints.push({
      id: "education",
      title: "교육비 직접 입력 점검",
      description: "부양가족 교육비는 대상자 연결이 중요합니다. 자녀 교육비가 있다면 deductions 화면에서 대상자를 확인해 주세요.",
      href: "/deductions"
    });
  }

  if (hints.length === 0) {
    hints.push({
      id: "review",
      title: "검토 완료 후 3단계 확정",
      description: "가져온 항목과 직접 입력 항목을 한 번 더 확인한 뒤 deductions 화면에서 3단계를 확정하면 됩니다.",
      href: "/deductions"
    });
  }

  return hints.slice(0, 3);
}
