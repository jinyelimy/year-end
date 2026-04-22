"use client";

import Link from "next/link";
import { useEffect, useMemo, useRef, useState, startTransition } from "react";
import { useRouter } from "next/navigation";

import StageThreeSidebar from "@/components/stage-three-sidebar";
import {
  clearAuth,
  createDeductionItem,
  deleteDeductionItem,
  getAccessToken,
  hasDeductionsConfirmed,
  hasIncomeConfirmed,
  initializeAuthenticatedContext,
  listDeductionItems,
  parseBasicInfo,
  saveCurrentSession,
  updateBasicInfo,
  updateDeductionItem
} from "@/lib/yearEndApi";
import {
  buildDeductionAttributes,
  getConfidenceMeta,
  getDeductionSource,
  getImportReviewMeta,
  getImportReviewReason,
  getLatestImportSummary,
  isDeductionIncludedInCalculation,
  isImportedDeduction,
  parseDeductionAttributes
} from "@/lib/deductionImport";
import {
  DEDUCTION_TYPE_OPTIONS,
  EVIDENCE_STATUS_OPTIONS,
  formatCurrency,
  formatDate,
  getDeductionTypeLabel,
  getEvidenceStatusLabel
} from "@/lib/yearEndView";

const SOURCE_FILTERS = [
  ["ALL", "전체", "전체 공제 항목"],
  ["HOMETAX", "홈택스 가져옴", "간소화 자료에서 가져온 항목"],
  ["MANUAL", "직접 입력", "직접 추가한 항목"],
  ["REVIEW", "확인 필요", "검토가 남아 있는 항목"]
];

const TYPE_META = {
  INSURANCE: {
    label: "보험료",
    desc: "보장성보험, 장애인전용보험",
    help: "보험사나 상품명을 남겨 두면 검토가 쉬워집니다.",
    subs: [
      { value: "보장성보험", label: "보장성보험" },
      { value: "장애인전용보험", label: "장애인전용보험" },
      { value: "실손보험", label: "실손보험" }
    ]
  },
  MEDICAL_EXPENSE: {
    label: "의료비",
    desc: "병원비, 약제비, 안경 구입비",
    help: "사용처와 지출일을 함께 남기면 추적이 쉬워집니다.",
    subs: [
      { value: "병원비", label: "병원비" },
      { value: "약제비", label: "약제비" },
      { value: "치과 치료비", label: "치과 치료비" },
      { value: "안경/렌즈", label: "안경/렌즈" },
      { value: "산후조리원", label: "산후조리원" },
      { value: "난임시술비", label: "난임시술비" }
    ]
  },
  EDUCATION_EXPENSE: {
    label: "교육비",
    desc: "본인·부양가족 교육비",
    help: "누구를 위한 교육비인지 세부 설명에 남겨 주세요.",
    subs: [
      { value: "본인 교육비", label: "본인 교육비" },
      { value: "취학 전 아동", label: "취학 전 아동" },
      { value: "초중고 교육비", label: "초중고 교육비" },
      { value: "대학교 등록금", label: "대학교 등록금" },
      { value: "학원비", label: "학원비" }
    ]
  },
  CREDIT_CARD: {
    label: "카드사용액",
    desc: "신용카드, 직불카드, 현금영수증",
    help: "카드 유형을 남겨 두면 나중에 비교가 쉽습니다.",
    subs: [
      { value: "신용카드", label: "신용카드" },
      { value: "직불카드", label: "직불카드" },
      { value: "현금영수증", label: "현금영수증" },
      { value: "전통시장", label: "전통시장" },
      { value: "대중교통", label: "대중교통" }
    ]
  },
  DONATION: {
    label: "기부금",
    desc: "일반기부금, 종교단체, 정치자금",
    help: "기부처와 영수증 상태를 함께 정리해 두면 좋습니다.",
    subs: [
      { value: "일반기부금", label: "일반기부금" },
      { value: "종교단체", label: "종교단체" },
      { value: "정치자금", label: "정치자금" },
      { value: "고향사랑기부", label: "고향사랑기부" }
    ]
  },
  LONG_TERM_COLLECTIVE_INVESTMENT: {
    label: "장기집합투자증권저축",
    desc: "소득공제용 장기집합투자증권저축 납입액",
    help: "연간 납입액을 한 항목으로 입력하면 됩니다. 세부 항목 구분은 없습니다.",
    subs: [
      { value: "납입액", label: "납입액" }
    ],
    strictSubType: true,
    singleItem: true
  },
  YOUTH_LONG_TERM_COLLECTIVE_INVESTMENT: {
    label: "청년형 장기집합투자증권저축",
    desc: "청년 대상 장기집합투자증권저축 납입액",
    help: "만 19~34세 청년 대상 저축입니다. 연간 납입액을 입력해 주세요.",
    subs: [
      { value: "납입액", label: "납입액" }
    ],
    strictSubType: true,
    singleItem: true
  },
  SME_MUTUAL_AID: {
    label: "소기업·소상공인 공제부금",
    desc: "노란우산공제 납입액 + 사업소득 기준",
    help: "'납입액'과 '사업소득 기준'을 각각 한 건씩 입력해야 한도가 정해집니다.",
    subs: [
      { value: "CONTRIBUTION", label: "납입액" },
      { value: "INCOME_BASIS", label: "사업소득 기준" }
    ],
    strictSubType: true
  },
  VENTURE_INVESTMENT: {
    label: "투자조합 출자",
    desc: "창업·벤처투자조합 출자금 소득공제",
    help: "직접 투자와 조합 출자는 공제율이 다르니 각각 입력해 주세요.",
    subs: [
      { value: "DIRECT", label: "직접 투자" },
      { value: "FUND", label: "조합 출자" }
    ],
    strictSubType: true
  },
  EMPLOYEE_STOCK_OWNERSHIP: {
    label: "우리사주조합 출연금",
    desc: "우리사주조합 가입자 출연금 공제",
    help: "'출연액'을 입력하고, 벤처기업 근로자라면 '벤처기업 여부'에 금액 1로 체크해 주세요.",
    subs: [
      { value: "CONTRIBUTION", label: "출연액" },
      { value: "VENTURE_EMPLOYER", label: "벤처기업 여부(1=예)" }
    ],
    strictSubType: true
  },
  FOREIGN_TAX_CREDIT: {
    label: "외국납부세액공제",
    desc: "국외원천소득에 대한 외국 납부세액 공제",
    help: "'외국 납부세액'과 '국외원천소득'을 각각 입력하면 비례한도가 계산됩니다.",
    subs: [
      { value: "FOREIGN_TAX_PAID", label: "외국 납부세액" },
      { value: "FOREIGN_SOURCE_INCOME", label: "국외원천소득" }
    ],
    strictSubType: true
  },
  TAX_PAYER_ASSOCIATION_CREDIT: {
    label: "납세조합공제",
    desc: "납세조합 징수 근로소득에 대한 5% 세액공제",
    help: "납세조합이 징수한 근로소득 금액을 한 건으로 입력하세요.",
    subs: [
      { value: "징수근로소득", label: "징수 근로소득" }
    ],
    strictSubType: true,
    singleItem: true
  },
  SME_YOUTH_EMPLOYEE_TAX_REDUCTION: {
    label: "중소기업 취업자 소득세 감면",
    desc: "청년·60세 이상·장애인·경력단절여성의 중소기업 취업 근로소득 감면",
    help: "'중소기업 근로소득'과 '감면 대상 유형'을 각각 입력하세요. 유형: 1=청년, 2=60세 이상, 3=장애인, 4=경력단절여성",
    subs: [
      { value: "EMPLOYER_INCOME", label: "중소기업 근로소득" },
      { value: "CATEGORY", label: "감면 대상 유형(1~4)" }
    ],
    strictSubType: true
  }
};

const baseForm = () => ({
  deductionType: "MEDICAL_EXPENSE",
  subType: TYPE_META.MEDICAL_EXPENSE.subs[0].value,
  amount: "0",
  usedAt: "",
  sourceName: "",
  evidenceStatus: "PENDING"
});

const num = (value) => {
  const parsed = Number(value || 0);
  return Number.isFinite(parsed) ? parsed : 0;
};

const sanitizeMoneyInput = (value) => String(value ?? "").replace(/[^\d]/g, "").replace(/^0+(?=\d)/, "");
const formatMoneyInput = (value) => value === "" || value === null || value === undefined ? "" : new Intl.NumberFormat("ko-KR").format(num(value));

function getTypeMeta(type) {
  return TYPE_META[type] || { label: getDeductionTypeLabel(type), desc: "세부 설명으로 구분해 주세요.", help: "세부 설명을 직접 입력해 주세요.", subs: [] };
}

function getCalcMeta(item) {
  const review = getImportReviewMeta(item);
  if (isDeductionIncludedInCalculation(item)) return { label: "계산 반영", badgeClass: "bg-emerald-100 text-emerald-700" };
  if (review.status === "PENDING") return { label: "검토 후 반영", badgeClass: "bg-amber-100 text-amber-700" };
  return { label: "계산 제외", badgeClass: "bg-slate-200 text-slate-600" };
}

function MessageBanner({ message }) {
  if (!message) return null;
  const tone = message.type === "success" ? "border-emerald-200 bg-emerald-50 text-emerald-700" : "border-red-200 bg-red-50 text-red-700";
  return <div className={`rounded-2xl border px-4 py-3 text-sm ${tone}`}>{message.text}</div>;
}

export default function DeductionsPage() {
  const router = useRouter();
  const pendingReviewBannerRef = useRef(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [message, setMessage] = useState(null);
  const [user, setUser] = useState(null);
  const [session, setSession] = useState(null);
  const [deductionItems, setDeductionItems] = useState([]);
  const [selectedItemId, setSelectedItemId] = useState(null);
  const [form, setForm] = useState(baseForm);
  const [sourceFilter, setSourceFilter] = useState("ALL");
  const [typeFilter, setTypeFilter] = useState("ALL");
  const isConfirmed = hasDeductionsConfirmed(session);

  useEffect(() => {
    if (!getAccessToken()) {
      startTransition(() => router.replace("/auth"));
      return;
    }
    let active = true;
    (async () => {
      try {
        const context = await initializeAuthenticatedContext();
        if (!hasIncomeConfirmed(context.currentSession)) {
          startTransition(() => router.replace("/income"));
          return;
        }
        const items = await listDeductionItems(context.currentSession.id);
        if (!active) return;
        setUser(context.user);
        setSession(context.currentSession);
        setDeductionItems(items);
        if (items[0]) selectItem(items[0]);
        setIsLoading(false);
      } catch {
        if (!active) return;
        clearAuth();
        startTransition(() => router.replace("/auth"));
      }
    })();
    return () => {
      active = false;
    };
  }, [router]);

  function resetForm() {
    setSelectedItemId(null);
    setForm(baseForm());
  }

  function selectItem(item) {
    setSelectedItemId(item.id);
    setForm({
      deductionType: item.deductionType || "MEDICAL_EXPENSE",
      subType: item.subType || "",
      amount: String(item.amount ?? 0),
      usedAt: item.usedAt || "",
      sourceName: item.sourceName || "",
      evidenceStatus: item.evidenceStatus || "PENDING"
    });
  }

  function updateType(type) {
    const meta = getTypeMeta(type);
    setForm((current) => ({
      ...current,
      deductionType: type,
      subType: current.deductionType === type ? current.subType : meta.subs[0]?.value || ""
    }));
  }

  async function syncDeductionsConfirmation(confirmed) {
    const basicInfo = parseBasicInfo(session);
    const updatedSession = await updateBasicInfo(session.id, {
      basicInfoJsonb: JSON.stringify({
        ...basicInfo,
        dependentsConfirmed: basicInfo.dependentsConfirmed === true,
        incomeConfirmed: basicInfo.incomeConfirmed === true,
        deductionsConfirmed: confirmed
      }),
      memo: session?.memo || ""
    });
    setSession(updatedSession);
    saveCurrentSession(updatedSession);
    return updatedSession;
  }

  async function reloadItems(nextSelectedId = selectedItemId) {
    const items = await listDeductionItems(session.id);
    setDeductionItems(items);
    if (!nextSelectedId) {
      resetForm();
      return;
    }
    const nextSelected = items.find((item) => item.id === nextSelectedId);
    if (nextSelected) selectItem(nextSelected);
    else resetForm();
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (!session?.id || isConfirmed) return;
    if (num(form.amount) < 0) {
      setMessage({ type: "error", text: "금액은 0원 이상이어야 합니다." });
      return;
    }

    setIsSaving(true);
    setMessage(null);
    try {
      const currentItem = deductionItems.find((item) => item.id === selectedItemId);
      const currentAttributes = currentItem ? parseDeductionAttributes(currentItem) : {};
      const sourceType = currentItem ? getDeductionSource(currentItem).type : "MANUAL";
      const nextAttributes = buildDeductionAttributes(currentAttributes, sourceType === "HOMETAX"
        ? { reviewStatus: "APPROVED", importBucket: "AUTO_APPLIED" }
        : { sourceType: "MANUAL", sourceLabel: "직접 입력", reviewStatus: "APPROVED" });
      const payload = {
        deductionType: form.deductionType,
        dependentId: currentItem?.dependentId || null,
        subType: form.subType.trim() || null,
        amount: num(form.amount),
        usedAt: form.usedAt || null,
        sourceName: form.sourceName.trim() || null,
        evidenceStatus: form.evidenceStatus,
        attributesJsonb: JSON.stringify(nextAttributes)
      };

      if (selectedItemId) {
        await updateDeductionItem(session.id, selectedItemId, payload);
        setMessage({
          type: "success",
          text: sourceType === "HOMETAX" ? "가져온 항목을 검토 완료 상태로 저장했습니다." : "공제 항목을 수정했습니다."
        });
        await reloadItems(selectedItemId);
      } else {
        await createDeductionItem(session.id, payload);
        setMessage({ type: "success", text: "직접 입력 공제를 추가했습니다." });
        await reloadItems(null);
      }

      await syncDeductionsConfirmation(false);
      if (!selectedItemId) resetForm();
    } catch (error) {
      setMessage({ type: "error", text: error.message || "공제 항목 저장에 실패했습니다." });
    } finally {
      setIsSaving(false);
    }
  }

  async function handleDelete() {
    if (!session?.id || !selectedItemId || isConfirmed) return;
    setIsSaving(true);
    setMessage(null);
    try {
      await deleteDeductionItem(session.id, selectedItemId);
      await syncDeductionsConfirmation(false);
      await reloadItems(null);
      setMessage({ type: "success", text: "공제 항목을 삭제했습니다." });
    } catch (error) {
      setMessage({ type: "error", text: error.message || "공제 항목 삭제에 실패했습니다." });
    } finally {
      setIsSaving(false);
    }
  }

  const pendingReviewItems = useMemo(
    () => deductionItems.filter((item) => getImportReviewMeta(item).status === "PENDING"),
    [deductionItems]
  );

  async function handleConfirmStep() {
    if (!session?.id) return;
    if (!isConfirmed && pendingReviewItems.length > 0) {
      setMessage({
        type: "error",
        text: "확인 필요 항목이 남아 있어 3단계를 확정할 수 없습니다. 검토를 완료한 뒤 다시 시도해 주세요."
      });
      pendingReviewBannerRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
      return;
    }

    setIsSaving(true);
    setMessage(null);
    try {
      if (isConfirmed) {
        await syncDeductionsConfirmation(false);
        setMessage({ type: "success", text: "3단계 확정을 해제했습니다. 다시 수정할 수 있습니다." });
      } else {
        await syncDeductionsConfirmation(true);
        setMessage({ type: "success", text: "3단계 확정을 완료했습니다. 대시보드로 이동합니다." });
        window.setTimeout(() => startTransition(() => router.replace("/")), 250);
      }
    } catch (error) {
      setMessage({ type: "error", text: error.message || "3단계 확정 처리에 실패했습니다." });
    } finally {
      setIsSaving(false);
    }
  }

  const sourceFilteredItems = useMemo(() => {
    switch (sourceFilter) {
      case "HOMETAX":
        return deductionItems.filter(isImportedDeduction);
      case "MANUAL":
        return deductionItems.filter((item) => !isImportedDeduction(item));
      case "REVIEW":
        return deductionItems.filter((item) => getImportReviewMeta(item).status === "PENDING");
      default:
        return deductionItems;
    }
  }, [deductionItems, sourceFilter]);

  const filteredItems = useMemo(() => {
    const scopedItems = typeFilter === "ALL"
      ? deductionItems
      : deductionItems.filter((item) => item.deductionType === typeFilter);

    return [...scopedItems].sort((left, right) => {
      const leftPriority = isImportedDeduction(left) ? 0 : 1;
      const rightPriority = isImportedDeduction(right) ? 0 : 1;

      if (leftPriority !== rightPriority) {
        return leftPriority - rightPriority;
      }

      return (left.usedAt || "").localeCompare(right.usedAt || "");
    });
  }, [deductionItems, typeFilter]);

  const selectedItem = deductionItems.find((item) => item.id === selectedItemId) || null;
  const selectedSource = selectedItem ? getDeductionSource(selectedItem) : null;
  const selectedReview = selectedItem ? getImportReviewMeta(selectedItem) : null;
  const selectedConfidence = selectedItem ? getConfidenceMeta(selectedItem) : null;
  const selectedReviewReason = selectedItem ? getImportReviewReason(selectedItem) : "";
  const selectedTypeMeta = getTypeMeta(form.deductionType);
  const isSingleItemDuplicate = !selectedItemId && selectedTypeMeta.singleItem &&
    deductionItems.some((item) => item.deductionType === form.deductionType);
  const totalDeduction = deductionItems.filter(isDeductionIncludedInCalculation).reduce((sum, item) => sum + (Number(item.amount) || 0), 0);
  const importedCount = deductionItems.filter(isImportedDeduction).length;
  const manualCount = deductionItems.length - importedCount;
  const latestImport = getLatestImportSummary(deductionItems);
  const typeCounts = useMemo(
    () => deductionItems.reduce((acc, item) => ({ ...acc, [item.deductionType]: (acc[item.deductionType] || 0) + 1 }), {}),
    [deductionItems]
  );
  const sourceCounts = {
    ALL: deductionItems.length,
    HOMETAX: importedCount,
    MANUAL: manualCount,
    REVIEW: pendingReviewItems.length
  };

  if (isLoading) {
    return <div className="flex min-h-screen items-center justify-center bg-background-light text-slate-500">공제 항목을 불러오고 있습니다...</div>;
  }

  return (
    <div className="min-h-screen bg-background-light font-display text-slate-900">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-[1520px] items-center justify-between px-4 py-4 sm:px-6 lg:px-8">
          <Link className="flex items-center gap-3 text-primary" href="/">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
              <span className="material-symbols-outlined">receipt_long</span>
            </div>
            <span className="text-lg font-bold tracking-tight text-slate-900">Ligg-Tax</span>
          </Link>
          <Link className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-50" href="/import-data">
            간소화 자료로 돌아가기
          </Link>
        </div>
      </header>

      <main className="mx-auto max-w-[1520px] px-4 py-8 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 gap-8 lg:grid-cols-12">
          <aside className="lg:col-span-3">
            <StageThreeSidebar activeStep="deductions" importedCount={importedCount} isConfirmed={isConfirmed} manualCount={manualCount} session={session} user={user} />
          </aside>

          <section className="space-y-6 lg:col-span-9">
            <div className="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm md:p-8">
              <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
                <div className="max-w-3xl">
                  <span className="inline-flex rounded-full bg-primary/10 px-3 py-1 text-xs font-semibold text-primary">3단계 · 공제 원장 정리</span>
                  <h1 className="mt-4 text-3xl font-black tracking-tight text-slate-900">가져온 공제와 직접 입력 공제를 한곳에서 정리합니다</h1>
                  <p className="mt-3 text-sm leading-7 text-slate-500">
                    이 화면은 출처와 검토 상태를 함께 보면서 최종 공제 원장을 정리하는 단계입니다.
                    <br />
                    홈택스에서 가져온 항목은 검토 이유와 함께 보고, 직접 입력 공제는 같은 원장에서 관리할 수 있습니다.
                  </p>
                </div>
                <div className="rounded-3xl border border-primary/20 bg-primary/5 px-6 py-5 lg:min-w-[260px]">
                  <p className="text-sm font-semibold text-slate-500">현재 반영 공제 합계</p>
                  <p className="mt-2 text-3xl font-black text-primary">{formatCurrency(totalDeduction)}</p>
                  <p className="mt-2 text-sm text-slate-500">검토 완료된 항목만 계산에 반영됩니다.</p>
                </div>
              </div>

              <div className="mt-6 space-y-3">
                {isConfirmed ? (
                  <div className="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-800">
                    3단계가 확정되어 입력과 수정이 잠겨 있습니다. 수정이 필요하면 아래에서 3단계 확정을 먼저 풀어 주세요.
                  </div>
                ) : null}
                {pendingReviewItems.length > 0 ? (
                  <div className="rounded-2xl border border-sky-200 bg-sky-50 px-4 py-3 text-sm text-sky-800" ref={pendingReviewBannerRef}>
                    아직 확인 필요 항목이 {pendingReviewItems.length}건 남아 있습니다. 검토 후 저장해야 3단계 확정이 가능합니다.
                  </div>
                ) : null}
                <MessageBanner message={message} />
              </div>

              <div className="mt-6 grid grid-cols-1 gap-4 md:grid-cols-4">
                {[
                  ["홈택스 가져옴", `${importedCount}건`],
                  ["직접 입력", `${manualCount}건`],
                  ["확인 필요", `${pendingReviewItems.length}건`],
                  ["최근 가져오기", latestImport?.fileName || "없음", latestImport?.importedAt ? formatDate(latestImport.importedAt) : "아직 가져온 파일이 없습니다."]
                ].map(([title, value, sub], index) => (
                  <div key={title} className="rounded-2xl border border-slate-200 bg-slate-50/70 p-5">
                    <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">{title}</p>
                    <p className={`mt-3 truncate font-black text-slate-900 ${index === 3 ? "text-sm leading-tight xl:text-base" : "text-3xl"}`}>{value}</p>
                    {sub ? <p className="mt-2 text-sm text-slate-500">{sub}</p> : null}
                  </div>
                ))}
              </div>
            </div>

            <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1.1fr)_minmax(340px,0.9fr)]">
              <div className="rounded-[30px] border border-slate-200 bg-white p-6 shadow-sm">
                <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
                  <div>
                    <h2 className="text-xl font-bold text-slate-900">공제 원장</h2>
                    <p className="mt-1 text-sm leading-6 text-slate-500">
                      공제 항목을 한곳에 모아 보고,
                      <br />
                      필요한 항목을 빠르게 정리하는 영역입니다.
                    </p>
                  </div>
                  <div className="flex w-full justify-end md:w-auto">
                    <button className="inline-flex min-h-[52px] items-center justify-center whitespace-nowrap rounded-xl border border-slate-200 bg-white px-5 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60" disabled={isConfirmed} onClick={resetForm} type="button">직접 입력 추가</button>
                  </div>
                </div>

                <div className="mt-5 rounded-3xl border border-slate-200 bg-slate-50/80 p-4">
                  <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">공제 분류 필터</p>
                  <div className="mt-3 flex flex-wrap gap-2">
                    <button className={`rounded-full border px-4 py-2 text-sm font-semibold transition ${typeFilter === "ALL" ? "border-slate-900 bg-slate-900 text-white" : "border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:bg-slate-100"}`} onClick={() => setTypeFilter("ALL")} type="button">
                      전체 분류 <span className="ml-1 text-xs opacity-80">{deductionItems.length}</span>
                    </button>
                    {DEDUCTION_TYPE_OPTIONS.map((option) => {
                      const meta = getTypeMeta(option.value);
                      const selected = typeFilter === option.value;
                      return (
                        <button key={option.value} className={`rounded-full border px-4 py-2 text-sm font-semibold transition ${selected ? "border-primary bg-primary/10 text-primary" : "border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:bg-slate-100"}`} onClick={() => setTypeFilter(option.value)} type="button">
                          {meta.label} <span className="ml-1 text-xs opacity-80">{typeCounts[option.value] || 0}</span>
                        </button>
                      );
                    })}
                  </div>
                </div>

                <div className="mt-5 flex items-center justify-between text-sm text-slate-500">
                  <p>현재 조건에 맞는 공제 {filteredItems.length}건</p>
                  <p>총 {formatCurrency(filteredItems.reduce((sum, item) => sum + (Number(item.amount) || 0), 0))}</p>
                </div>

                {filteredItems.length === 0 ? (
                  <div className="mt-5 rounded-3xl border border-dashed border-slate-200 bg-slate-50 px-4 py-10 text-center text-sm text-slate-500">
                    현재 조건에 맞는 공제 항목이 없습니다. 필터를 바꾸거나 직접 입력으로 추가해 보세요.
                  </div>
                ) : (
                  <div className="mt-5 space-y-3">
                    {filteredItems.map((item) => {
                      const source = getDeductionSource(item);
                      const review = getImportReviewMeta(item);
                      const confidence = getConfidenceMeta(item);
                      const typeMeta = getTypeMeta(item.deductionType);
                      const calc = getCalcMeta(item);
                      const active = item.id === selectedItemId;
                      return (
                        <button key={item.id} className={`w-full rounded-3xl border px-4 py-4 text-left transition ${active ? "border-sky-300 bg-sky-50 shadow-[inset_4px_0_0_0_rgb(56,189,248)]" : "border-slate-200 bg-white hover:border-primary/40 hover:shadow-sm"}`} onClick={() => selectItem(item)} type="button">
                          <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                            <div className="min-w-0 flex-1">
                              <div className="flex flex-wrap items-center gap-2">
                                <p className="text-base font-bold text-slate-900">{typeMeta.label}</p>
                                <span className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${source.badgeClass}`}>{source.label}</span>
                                <span className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${review.badgeClass}`}>{review.label}</span>
                                <span className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${calc.badgeClass}`}>{calc.label}</span>
                              </div>
                              <p className="mt-2 text-sm font-medium text-slate-700">{item.subType || typeMeta.desc}</p>
                              <p className="mt-1 text-sm text-slate-500">{item.sourceName || "출처 정보 없음"}{item.usedAt ? ` · ${formatDate(item.usedAt)}` : ""}</p>
                              <div className="mt-3 flex flex-wrap gap-2">
                                <span className="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-semibold text-slate-600">증빙 {getEvidenceStatusLabel(item.evidenceStatus)}</span>
                                {isImportedDeduction(item) ? <span className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${confidence.badgeClass}`}>신뢰도 {confidence.label}</span> : null}
                              </div>
                            </div>
                            <div className="text-left sm:text-right">
                              <p className="text-xl font-black text-primary">{formatCurrency(item.amount)}</p>
                              {isImportedDeduction(item) && review.status === "PENDING" ? (
                                <p className="mt-2 max-w-[220px] text-xs leading-5 text-amber-700 sm:ml-auto">{getImportReviewReason(item) || "검토 사유를 확인해 주세요."}</p>
                              ) : null}
                            </div>
                          </div>
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>

              <div className="rounded-[30px] border border-slate-200 bg-slate-50/70 p-6">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <h2 className="text-xl font-bold text-slate-900">{selectedItemId ? "공제 항목 검토 / 수정" : "직접 입력 공제 추가"}</h2>
                    <p className="mt-1 text-sm leading-6 text-slate-500">
                      분류를 먼저 고르고,
                      <br />
                      세부 항목과 증빙 상태를 채우는 순서로 정리합니다.
                    </p>
                  </div>
                  {selectedItemId ? <button className="rounded-lg border border-red-200 bg-red-50 px-3 py-1.5 text-xs font-semibold text-red-600 transition hover:bg-red-100 disabled:opacity-60" disabled={isConfirmed || isSaving} onClick={handleDelete} type="button">삭제</button> : null}
                </div>

                <div className="mt-5 rounded-3xl border border-slate-200 bg-white px-4 py-4">
                  <p className="text-sm font-semibold text-slate-700">입력 항목 안내</p>
                  <p className="mt-2 text-sm leading-6 text-slate-500">기본 5종(보험료, 의료비, 교육비, 카드사용액, 기부금)과 함께 장기집합투자·노란우산·투자조합·우리사주·외국납부·납세조합 등 연간 누계형 공제도 입력할 수 있습니다. 연간 누계 공제는 한 해 합산 금액으로 한 번만 입력해 주세요.</p>
                </div>

                {selectedItem ? (
                  <div className="mt-5 rounded-3xl border border-white bg-white p-4">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${selectedSource?.badgeClass || "bg-slate-100 text-slate-600"}`}>{selectedSource?.label || "출처 없음"}</span>
                      <span className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${selectedReview?.badgeClass || "bg-slate-100 text-slate-600"}`}>{selectedReview?.label || "검토 정보 없음"}</span>
                      {selectedConfidence ? <span className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${selectedConfidence.badgeClass}`}>신뢰도 {selectedConfidence.label}</span> : null}
                    </div>
                    {selectedReviewReason ? <p className="mt-3 text-sm leading-6 text-slate-600">{selectedReviewReason}</p> : null}
                  </div>
                ) : null}

                <form className="mt-5 space-y-5" onSubmit={handleSubmit}>
                  <section className="rounded-3xl border border-white bg-white p-5">
                    <div className="flex items-center gap-2">
                      <span className="inline-flex h-7 w-7 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary">1</span>
                      <div>
                        <h3 className="text-base font-bold text-slate-900">어떤 공제인가요?</h3>
                        <p className="text-sm text-slate-500">대분류를 먼저 고르면 아래 입력 순서가 더 쉬워집니다.</p>
                      </div>
                    </div>
                    <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
                      {DEDUCTION_TYPE_OPTIONS.map((option) => {
                        const meta = getTypeMeta(option.value);
                        const selected = form.deductionType === option.value;
                        return (
                          <button key={option.value} className={`rounded-2xl border px-4 py-4 text-left transition ${selected ? "border-primary bg-primary/10 shadow-[inset_0_0_0_1px_rgba(14,165,233,0.25)]" : "border-slate-200 bg-slate-50 hover:border-slate-300 hover:bg-slate-100"}`} onClick={() => updateType(option.value)} type="button">
                            <div className="flex items-center justify-between gap-3">
                              <p className="font-bold text-slate-900">{meta.label}</p>
                              <span className="rounded-full bg-white/80 px-2 py-1 text-[11px] font-semibold text-slate-500">{typeCounts[option.value] || 0}건</span>
                            </div>
                            <p className="mt-2 text-sm leading-6 text-slate-500">{meta.desc}</p>
                          </button>
                        );
                      })}
                    </div>
                  </section>

                  <section className="rounded-3xl border border-white bg-white p-5">
                    <div className="flex items-center gap-2">
                      <span className="inline-flex h-7 w-7 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary">2</span>
                      <div>
                        <h3 className="text-base font-bold text-slate-900">세부 항목을 적어 주세요</h3>
                        <p className="text-sm text-slate-500">{selectedTypeMeta.help}</p>
                      </div>
                    </div>
                    <div className="mt-4 flex flex-wrap gap-2">
                      {selectedTypeMeta.subs.map((sub) => {
                        const selected = form.subType === sub.value;
                        return (
                          <button key={sub.value} className={`rounded-full border px-3 py-2 text-sm font-semibold transition ${selected ? "border-primary bg-primary text-white" : "border-slate-200 bg-slate-50 text-slate-600 hover:border-slate-300 hover:bg-slate-100"}`} onClick={() => setForm((current) => ({ ...current, subType: sub.value }))} type="button">
                            {sub.label}
                          </button>
                        );
                      })}
                    </div>
                    {selectedTypeMeta.strictSubType ? (
                      <p className="mt-3 text-xs leading-5 text-slate-500">이 공제는 위 분류 중 하나를 그대로 선택해 주세요. 자유 입력은 계산에 반영되지 않습니다.</p>
                    ) : (
                      <div className="mt-4 space-y-2">
                        <label className="block text-sm font-semibold text-slate-700" htmlFor="sub-type">세부 설명</label>
                        <input className={`h-12 w-full rounded-xl border px-4 text-sm ${isConfirmed ? "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400" : "border-slate-200 bg-white focus:border-primary focus:ring-primary"}`} disabled={isConfirmed} id="sub-type" onChange={(event) => setForm((current) => ({ ...current, subType: event.target.value }))} placeholder={selectedTypeMeta.subs[0]?.label || "예: 병원비, 현금영수증, 종교단체"} type="text" value={form.subType} />
                      </div>
                    )}
                  </section>

                  <section className="rounded-3xl border border-white bg-white p-5">
                    <div className="flex items-center gap-2">
                      <span className="inline-flex h-7 w-7 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary">3</span>
                      <div>
                        <h3 className="text-base font-bold text-slate-900">금액과 사용 정보를 입력해 주세요</h3>
                        <p className="text-sm text-slate-500">계산과 증빙 연결에 필요한 기본 정보입니다.</p>
                      </div>
                    </div>
                    <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
                      <div className="space-y-2 sm:col-span-2">
                        <label className="block text-sm font-semibold text-slate-700" htmlFor="amount">금액</label>
                        <input className={`h-14 w-full rounded-xl border px-4 text-right font-mono text-lg font-bold ${isConfirmed ? "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400" : "border-slate-200 bg-white focus:border-primary focus:ring-primary"}`} disabled={isConfirmed} id="amount" inputMode="numeric" onChange={(event) => setForm((current) => ({ ...current, amount: sanitizeMoneyInput(event.target.value) }))} type="text" value={formatMoneyInput(form.amount)} />
                      </div>
                      <div className="space-y-2">
                        <label className="block text-sm font-semibold text-slate-700" htmlFor="used-at">사용일</label>
                        <input className={`h-12 w-full rounded-xl border px-4 text-sm ${isConfirmed ? "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400" : "border-slate-200 bg-white focus:border-primary focus:ring-primary"}`} disabled={isConfirmed} id="used-at" onChange={(event) => setForm((current) => ({ ...current, usedAt: event.target.value }))} type="date" value={form.usedAt} />
                      </div>
                      <div className="space-y-2">
                        <label className="block text-sm font-semibold text-slate-700" htmlFor="source-name">사용처 / 출처</label>
                        <input className={`h-12 w-full rounded-xl border px-4 text-sm ${isConfirmed ? "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400" : "border-slate-200 bg-white focus:border-primary focus:ring-primary"}`} disabled={isConfirmed} id="source-name" onChange={(event) => setForm((current) => ({ ...current, sourceName: event.target.value }))} placeholder="예: 서울종합병원, 삼성카드, 사랑의열매" type="text" value={form.sourceName} />
                      </div>
                    </div>
                  </section>

                  <section className="rounded-3xl border border-white bg-white p-5">
                    <div className="flex items-center gap-2">
                      <span className="inline-flex h-7 w-7 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary">4</span>
                      <div>
                        <h3 className="text-base font-bold text-slate-900">증빙 상태를 체크해 주세요</h3>
                        <p className="text-sm text-slate-500">증빙 체크리스트 연결과 검토 상태에 활용됩니다.</p>
                      </div>
                    </div>
                    <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-3">
                      {EVIDENCE_STATUS_OPTIONS.map((option) => {
                        const selected = form.evidenceStatus === option.value;
                        return (
                          <button key={option.value} className={`rounded-2xl border px-4 py-3 text-left transition ${selected ? "border-primary bg-primary/10 text-primary" : "border-slate-200 bg-slate-50 text-slate-600 hover:border-slate-300 hover:bg-slate-100"} ${isConfirmed ? "cursor-not-allowed opacity-60" : ""}`} disabled={isConfirmed} onClick={() => setForm((current) => ({ ...current, evidenceStatus: option.value }))} type="button">
                            <p className="font-semibold">{option.label}</p>
                          </button>
                        );
                      })}
                    </div>
                  </section>

                  {!isConfirmed ? (
                    <div className="flex flex-col gap-3">
                      {isSingleItemDuplicate ? (
                        <p className="text-right text-xs font-semibold text-amber-600">이 공제는 한 건만 입력할 수 있습니다. 기존 항목을 선택해 수정해 주세요.</p>
                      ) : null}
                      <div className="flex justify-end gap-3">
                        <button className="rounded-xl border border-slate-200 px-5 py-2.5 text-sm font-medium transition hover:bg-slate-50" onClick={resetForm} type="button">초기화</button>
                        <button className="rounded-xl bg-primary px-6 py-2.5 text-sm font-semibold text-white shadow-md transition hover:bg-primary/90 disabled:opacity-60" disabled={isSaving || isSingleItemDuplicate} type="submit">
                          {isSaving ? "저장 중..." : selectedItemId && selectedSource?.type === "HOMETAX" ? "검토 완료로 저장" : selectedItemId ? "공제 항목 수정" : "공제 항목 추가"}
                        </button>
                      </div>
                    </div>
                  ) : null}
                </form>
              </div>
            </div>

            <div className="flex items-center justify-between rounded-3xl border border-slate-200 bg-white px-6 py-5 shadow-sm">
              <button className="rounded-xl border border-slate-200 bg-white px-6 py-2.5 text-sm font-semibold text-slate-600 transition hover:border-slate-300 hover:bg-slate-50" onClick={() => startTransition(() => router.push("/"))} type="button">
                대시보드로 이동
              </button>
              <button className={`rounded-xl px-6 py-2.5 text-sm font-semibold text-white transition ${isConfirmed ? "bg-red-500 hover:bg-red-600" : "bg-primary hover:bg-primary/90"}`} disabled={isSaving} onClick={handleConfirmStep} type="button">
                {isConfirmed ? "3단계 확정 풀기" : "3단계 확정"}
              </button>
            </div>
          </section>
        </div>
      </main>
    </div>
  );
}
