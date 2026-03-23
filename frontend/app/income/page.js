"use client";

import Link from "next/link";
import { useEffect, useState, startTransition } from "react";
import { useRouter } from "next/navigation";

import {
  clearAuth,
  createIncomeItem,
  deleteIncomeItem,
  getAccessToken,
  hasDependentsConfirmed,
  hasIncomeConfirmed,
  initializeAuthenticatedContext,
  listIncomeItems,
  parseBasicInfo,
  saveCurrentSession,
  updateBasicInfo,
  updateIncomeItem
} from "@/lib/yearEndApi";
import {
  calculateFinancialSummary,
  formatCurrency,
  getIncomeTypeLabel
} from "@/lib/yearEndView";

const INITIAL_PRIMARY_FORM = {
  payerName: "",
  totalSalary: "0",
  nonTaxableAmount: "0",
  withheldTaxAmount: "0"
};

const INITIAL_DETAIL_FORM = {
  incomeType: "SALARY",
  payerName: "",
  grossAmount: "0",
  taxableAmount: "0",
  withheldTaxAmount: "0",
  nonTaxableAmount: "0"
};

const DETAIL_TYPE_OPTIONS = [
  { value: "SALARY", label: "종전 근무지" },
  { value: "OTHER_INCOME", label: "기타소득" }
];

function parseIncomeAttributes(item) {
  if (!item?.attributesJsonb) {
    return {};
  }

  try {
    return JSON.parse(item.attributesJsonb);
  } catch {
    return {};
  }
}

function getIncomeEntryKind(item) {
  const attributes = parseIncomeAttributes(item);

  if (attributes.entryKind) {
    return attributes.entryKind;
  }

  if (item?.incomeType === "OTHER_INCOME") {
    return "OTHER_INCOME";
  }

  if (item?.incomeType === "BONUS") {
    return "BONUS_ADJUSTMENT";
  }

  if (item?.incomeType === "SALARY") {
    return "LEGACY_SALARY";
  }

  return "UNKNOWN";
}

function getPrimaryIncomeItem(items) {
  const primaryItem = items.find((item) => getIncomeEntryKind(item) === "PRIMARY_SALARY");
  if (primaryItem) {
    return primaryItem;
  }

  return items.find(
    (item) => item.incomeType === "SALARY" && getIncomeEntryKind(item) === "LEGACY_SALARY"
  ) || null;
}

function getExceptionItemLabel(item) {
  const entryKind = getIncomeEntryKind(item);

  if (entryKind === "PREVIOUS_WORKPLACE") {
    return "종전 근무지";
  }

  if (entryKind === "OTHER_INCOME") {
    return "기타소득";
  }

  if (entryKind === "BONUS_ADJUSTMENT") {
    return "예외 상여";
  }

  return getIncomeTypeLabel(item?.incomeType);
}

function toNumber(value) {
  const parsed = Number(value || 0);
  return Number.isFinite(parsed) ? parsed : 0;
}

function sanitizeMoneyInput(value) {
  const digits = String(value ?? "").replace(/[^\d]/g, "");
  return digits.replace(/^0+(?=\d)/, "");
}

function formatMoneyInput(value) {
  if (value === null || value === undefined || value === "") {
    return "";
  }

  return new Intl.NumberFormat("ko-KR").format(toNumber(value));
}

function buildPrimaryForm(salaryItem) {
  if (!salaryItem) {
    return INITIAL_PRIMARY_FORM;
  }

  return {
    payerName: salaryItem.payerName || "",
    totalSalary: String(salaryItem.grossAmount ?? 0),
    nonTaxableAmount: String(salaryItem.nonTaxableAmount ?? 0),
    withheldTaxAmount: String(salaryItem.withheldTaxAmount ?? 0)
  };
}

function buildDetailForm(item) {
  if (!item) {
    return INITIAL_DETAIL_FORM;
  }

  const entryKind = getIncomeEntryKind(item);
  const detailIncomeType = entryKind === "PREVIOUS_WORKPLACE"
    ? "SALARY"
    : entryKind === "BONUS_ADJUSTMENT"
      ? "BONUS"
      : item.incomeType || "SALARY";

  return {
    incomeType: detailIncomeType,
    payerName: item.payerName || "",
    grossAmount: String(item.grossAmount ?? 0),
    taxableAmount: String(item.taxableAmount ?? 0),
    withheldTaxAmount: String(item.withheldTaxAmount ?? 0),
    nonTaxableAmount: String(item.nonTaxableAmount ?? 0)
  };
}

function calculateTaxableSalary(totalSalary, nonTaxableAmount) {
  return Math.max(toNumber(totalSalary) - toNumber(nonTaxableAmount), 0);
}

function validatePrimaryForm(form) {
  const totalSalary = toNumber(form.totalSalary);
  const nonTaxableAmount = toNumber(form.nonTaxableAmount);
  const withheldTaxAmount = toNumber(form.withheldTaxAmount);

  if (totalSalary < 0 || nonTaxableAmount < 0 || withheldTaxAmount < 0) {
    return "금액은 0원 이상이어야 합니다.";
  }

  if (nonTaxableAmount > totalSalary) {
    return "비과세 금액은 총급여를 초과할 수 없습니다.";
  }

  return null;
}

function validateDetailForm(form) {
  const grossAmount = toNumber(form.grossAmount);
  const taxableAmount = toNumber(form.taxableAmount);
  const withheldTaxAmount = toNumber(form.withheldTaxAmount);
  const nonTaxableAmount = toNumber(form.nonTaxableAmount);

  if (!form.payerName.trim()) {
    return "상세 소득은 지급처명을 입력해야 합니다.";
  }

  if ([grossAmount, taxableAmount, withheldTaxAmount, nonTaxableAmount].some((value) => value < 0)) {
    return "금액은 0원 이상이어야 합니다.";
  }

  if (taxableAmount + nonTaxableAmount > grossAmount) {
    return "과세 금액과 비과세 금액의 합은 총액을 넘을 수 없습니다.";
  }

  return null;
}

function MessageBanner({ message }) {
  if (!message) {
    return null;
  }

  const tone = message.type === "success"
    ? "border-emerald-200 bg-emerald-50 text-emerald-700"
    : "border-red-200 bg-red-50 text-red-700";

  return <div className={`mb-6 rounded-xl border px-4 py-3 text-sm ${tone}`}>{message.text}</div>;
}

function Sidebar({ user, session, isConfirmed }) {
  const displayName = `${user?.name || "사용자"}님`;
  const avatarText = (user?.name || "Y").slice(0, 1).toUpperCase();

  return (
    <div className="sticky top-24 space-y-6">
      <div className="rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm">
        <div className="mb-7 flex items-center gap-4">
          <div className="flex h-12 w-12 items-center justify-center rounded-full bg-primary text-lg font-bold text-white">
            {avatarText}
          </div>
          <div>
            <h2 className="text-lg font-bold leading-tight text-slate-900">{displayName}</h2>
            <p className="mt-1 text-sm font-medium text-slate-500">{isConfirmed ? "2단계 완료" : "2단계 진행 중"}</p>
          </div>
        </div>

        <nav className="space-y-2">
          <Link className="hidden" href="/basic-info">
            <span className="material-symbols-outlined text-[1.9rem]">person</span>
            <span className="text-sm font-semibold">기본정보</span>
          </Link>
          <Link className="hidden" href="/dependents">
            <span className="material-symbols-outlined text-[1.9rem]">group</span>
            <span className="text-sm font-semibold">부양가족</span>
          </Link>
          <Link className="flex items-center gap-3 rounded-xl bg-primary/10 px-4 py-3 text-primary" href="/income">
            <span className="material-symbols-outlined text-[1.9rem]">payments</span>
            <span className="text-sm font-semibold">소득확인</span>
          </Link>
        </nav>
      </div>

      <div className="rounded-2xl border border-primary/20 bg-primary/5 p-5">
        <h3 className="text-sm font-bold text-primary">2단계 안내</h3>
        <p className="mt-2 text-sm leading-6 text-slate-600">
          대부분의 근로소득자는 총급여, 비과세, 기납부세액만 입력하면 됩니다. 종전 근무지나 기타소득처럼 예외적인 경우에만 아래 추가 입력을 사용하세요.
        </p>
        <p className="mt-3 text-xs text-slate-500">세션 연도: {session?.taxYear}년 귀속</p>
      </div>
    </div>
  );
}

export default function IncomePage() {
  const router = useRouter();
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [message, setMessage] = useState(null);
  const [user, setUser] = useState(null);
  const [session, setSession] = useState(null);
  const [incomeItems, setIncomeItems] = useState([]);
  const [primaryForm, setPrimaryForm] = useState(INITIAL_PRIMARY_FORM);
  const [detailForm, setDetailForm] = useState(INITIAL_DETAIL_FORM);
  const [selectedDetailId, setSelectedDetailId] = useState(null);
  const [isDetailOpen, setIsDetailOpen] = useState(false);

  function updatePrimaryMoneyField(field) {
    return (event) => {
      const nextValue = sanitizeMoneyInput(event.target.value);
      setPrimaryForm((current) => ({ ...current, [field]: nextValue }));
    };
  }

  function updateDetailMoneyField(field) {
    return (event) => {
      const nextValue = sanitizeMoneyInput(event.target.value);
      setDetailForm((current) => ({ ...current, [field]: nextValue }));
    };
  }

  const primaryIncomeItem = getPrimaryIncomeItem(incomeItems);
  const additionalItems = incomeItems.filter((item) => item.id !== primaryIncomeItem?.id);
  const summary = calculateFinancialSummary(incomeItems, []);
  const isConfirmed = hasIncomeConfirmed(session);
  const taxableSalary = calculateTaxableSalary(primaryForm.totalSalary, primaryForm.nonTaxableAmount);
  const isPreviousWorkplace = detailForm.incomeType === "SALARY";
  const detailPayerLabel = isPreviousWorkplace ? "종전 근무지명" : "소득 발생처";
  const detailGrossLabel = isPreviousWorkplace ? "종전 근무지 총급여" : "기타소득 총액";
  const detailTaxableLabel = isPreviousWorkplace ? "과세 대상 급여" : "과세 대상 기타소득";
  const detailWithheldLabel = isPreviousWorkplace ? "종전 근무지 기납부세액" : "원천징수세액";

  useEffect(() => {
    if (!getAccessToken()) {
      startTransition(() => {
        router.replace("/auth");
      });
      return;
    }

    let active = true;

    (async () => {
      try {
        const context = await initializeAuthenticatedContext();
        if (!hasDependentsConfirmed(context.currentSession)) {
          startTransition(() => {
            router.replace("/dependents");
          });
          return;
        }

        const items = await listIncomeItems(context.currentSession.id);
        if (!active) {
          return;
        }

        setUser(context.user);
        setSession(context.currentSession);
        setIncomeItems(items);
        setPrimaryForm(buildPrimaryForm(getPrimaryIncomeItem(items)));

        const firstAdditional = items.find((item) => item.id !== getPrimaryIncomeItem(items)?.id);
        if (firstAdditional) {
          setSelectedDetailId(firstAdditional.id);
          setDetailForm(buildDetailForm(firstAdditional));
        }

        setIsLoading(false);
      } catch {
        if (!active) {
          return;
        }

        clearAuth();
        startTransition(() => {
          router.replace("/auth");
        });
      }
    })();

    return () => {
      active = false;
    };
  }, [router]);

  function openNewDetailForm() {
    setSelectedDetailId(null);
    setDetailForm(INITIAL_DETAIL_FORM);
    setIsDetailOpen(true);
  }

  function selectDetailItem(item) {
    setSelectedDetailId(item.id);
    setDetailForm(buildDetailForm(item));
    setIsDetailOpen(true);
  }

  async function refreshIncomeItems(nextSelectedDetailId = selectedDetailId) {
    const items = await listIncomeItems(session.id);
    setIncomeItems(items);
    const nextPrimaryIncomeItem = getPrimaryIncomeItem(items);
    setPrimaryForm(buildPrimaryForm(nextPrimaryIncomeItem));

    const selectedAdditional = items.find(
      (item) => item.id === nextSelectedDetailId && item.id !== nextPrimaryIncomeItem?.id
    );
    const fallbackAdditional = items.find((item) => item.id !== nextPrimaryIncomeItem?.id);
    const nextAdditional = selectedAdditional || fallbackAdditional;

    if (nextAdditional) {
      setSelectedDetailId(nextAdditional.id);
      setDetailForm(buildDetailForm(nextAdditional));
    } else {
      setSelectedDetailId(null);
      setDetailForm(INITIAL_DETAIL_FORM);
      setIsDetailOpen(false);
    }
  }

  async function syncIncomeConfirmation(confirmed) {
    const basicInfo = parseBasicInfo(session);
    const updatedSession = await updateBasicInfo(session.id, {
      basicInfoJsonb: JSON.stringify({
        ...basicInfo,
        dependentsConfirmed: basicInfo.dependentsConfirmed === true,
        incomeConfirmed: confirmed
      }),
      memo: session?.memo || ""
    });

    setSession(updatedSession);
    saveCurrentSession(updatedSession);
    return updatedSession;
  }

  async function handlePrimarySave(event) {
    event.preventDefault();
    if (!session?.id || isConfirmed) {
      return;
    }

    const validationError = validatePrimaryForm(primaryForm);
    if (validationError) {
      setMessage({ type: "error", text: validationError });
      return;
    }

    setIsSaving(true);
    setMessage(null);

    try {
      const payload = {
        incomeType: "SALARY",
        payerName: primaryForm.payerName.trim() || "현 근무지",
        grossAmount: toNumber(primaryForm.totalSalary),
        taxableAmount: taxableSalary,
        withheldTaxAmount: toNumber(primaryForm.withheldTaxAmount),
        nonTaxableAmount: toNumber(primaryForm.nonTaxableAmount),
        attributesJsonb: JSON.stringify({
          entryKind: "PRIMARY_SALARY",
          compensationScope: "SALARY_AND_BONUS"
        })
      };

      if (primaryIncomeItem) {
        await updateIncomeItem(session.id, primaryIncomeItem.id, payload);
      } else {
        await createIncomeItem(session.id, payload);
      }

      await syncIncomeConfirmation(false);
      await refreshIncomeItems();
      setMessage({ type: "success", text: "기본 소득 정보를 저장했습니다." });
    } catch (error) {
      setMessage({ type: "error", text: error.message || "기본 소득 저장에 실패했습니다." });
    } finally {
      setIsSaving(false);
    }
  }

  async function handleDetailSave(event) {
    event.preventDefault();
    if (!session?.id || isConfirmed) {
      return;
    }

    const validationError = validateDetailForm(detailForm);
    if (validationError) {
      setMessage({ type: "error", text: validationError });
      return;
    }

    setIsSaving(true);
    setMessage(null);

    try {
      const entryKind = detailForm.incomeType === "SALARY"
        ? "PREVIOUS_WORKPLACE"
        : detailForm.incomeType === "BONUS"
          ? "BONUS_ADJUSTMENT"
          : "OTHER_INCOME";

      const payload = {
        incomeType: detailForm.incomeType,
        payerName: detailForm.payerName.trim(),
        grossAmount: toNumber(detailForm.grossAmount),
        taxableAmount: toNumber(detailForm.taxableAmount),
        withheldTaxAmount: toNumber(detailForm.withheldTaxAmount),
        nonTaxableAmount: toNumber(detailForm.nonTaxableAmount),
        attributesJsonb: JSON.stringify({ entryKind })
      };

      if (selectedDetailId) {
        await updateIncomeItem(session.id, selectedDetailId, payload);
        await refreshIncomeItems(selectedDetailId);
        setMessage({ type: "success", text: "상세 소득 항목을 수정했습니다." });
      } else {
        await createIncomeItem(session.id, payload);
        await refreshIncomeItems();
        openNewDetailForm();
        setMessage({ type: "success", text: "상세 소득 항목을 추가했습니다." });
      }

      await syncIncomeConfirmation(false);
    } catch (error) {
      setMessage({ type: "error", text: error.message || "상세 소득 저장에 실패했습니다." });
    } finally {
      setIsSaving(false);
    }
  }

  async function handleDetailDelete() {
    if (!session?.id || !selectedDetailId || isConfirmed) {
      return;
    }

    setIsSaving(true);
    setMessage(null);

    try {
      await deleteIncomeItem(session.id, selectedDetailId);
      await syncIncomeConfirmation(false);
      await refreshIncomeItems(null);
      setMessage({ type: "success", text: "상세 소득 항목을 삭제했습니다." });
    } catch (error) {
      setMessage({ type: "error", text: error.message || "상세 소득 삭제에 실패했습니다." });
    } finally {
      setIsSaving(false);
    }
  }

  async function handleConfirmStep() {
    if (!session?.id) {
      return;
    }

    setIsSaving(true);
    setMessage(null);

    try {
      if (isConfirmed) {
        await syncIncomeConfirmation(false);
        setMessage({ type: "success", text: "2단계 확정을 풀었습니다. 다시 수정할 수 있습니다." });
      } else {
        await syncIncomeConfirmation(true);
        setMessage({ type: "success", text: "2단계 확정을 완료했습니다. 대시보드로 이동합니다." });
        window.setTimeout(() => {
          startTransition(() => {
            router.replace("/");
          });
        }, 250);
      }
    } catch (error) {
      setMessage({ type: "error", text: error.message || "2단계 확정 처리에 실패했습니다." });
    } finally {
      setIsSaving(false);
    }
  }

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background-light text-slate-500">
        소득 정보를 불러오는 중입니다...
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background-light font-display text-slate-900">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-4 sm:px-6 lg:px-8">
          <Link className="flex items-center gap-3 text-primary" href="/">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
              <span className="material-symbols-outlined">payments</span>
            </div>
            <span className="text-lg font-bold tracking-tight text-slate-900">Easy-Tax</span>
          </Link>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 gap-8 lg:grid-cols-12">
          <aside className="lg:col-span-3">
            <Sidebar isConfirmed={isConfirmed} session={session} user={user} />
          </aside>

          <section className="lg:col-span-9">
            <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm md:p-8">
              <div className="mb-8">
                <h2 className="text-2xl font-bold text-slate-900">소득 확인</h2>
                <p className="mt-2 text-sm text-slate-500">
                  기본 입력은 총급여 기준으로 단순화했고, 종전 근무지나 기타소득처럼 예외적인 경우만 아래의 추가 입력을 사용하도록 정리했습니다.
                </p>
              </div>

              {isConfirmed ? (
                <div className="mb-6 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-800">
                  2단계가 확정되어 입력이 잠겨 있습니다. 수정이 필요하면 아래의 `2단계 확정 풀기`를 눌러 주세요.
                </div>
              ) : null}

              <div className="mb-8 grid grid-cols-1 gap-4 md:grid-cols-4">
                <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                  <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">총 지급액</p>
                  <p className="mt-3 whitespace-nowrap text-[1.15rem] font-black leading-none tracking-tight text-slate-900 md:text-[1.25rem] xl:text-[1.4rem]">{formatCurrency(summary.totalGrossIncome)}</p>
                </div>
                <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                  <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">과세 소득</p>
                  <p className="mt-3 whitespace-nowrap text-[1.15rem] font-black leading-none tracking-tight text-primary md:text-[1.25rem] xl:text-[1.4rem]">{formatCurrency(summary.totalTaxableIncome)}</p>
                </div>
                <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                  <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">비과세 소득</p>
                  <p className="mt-3 whitespace-nowrap text-[1.15rem] font-black leading-none tracking-tight text-slate-900 md:text-[1.25rem] xl:text-[1.4rem]">{formatCurrency(summary.totalNonTaxableIncome)}</p>
                </div>
                <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                  <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">기납부세액</p>
                  <p className="mt-3 whitespace-nowrap text-[1.15rem] font-black leading-none tracking-tight text-slate-900 md:text-[1.25rem] xl:text-[1.4rem]">{formatCurrency(summary.totalWithheldTax)}</p>
                </div>
              </div>

              <MessageBanner message={message} />

              <div className="space-y-8">
                <section className="rounded-2xl border border-slate-200 bg-slate-50/60 p-6">
                  <div className="mb-6 flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                    <div className="w-full">
                      <h3 className="text-lg font-bold text-slate-900">기본 입력</h3>
                      <div className="mt-2 text-sm leading-5 text-slate-500">
                        <p>일반적인 근로소득자는 이 영역만 입력해도 됩니다.</p>
                        <p>총급여에는 보통 급여와 상여가 함께 포함되며, 과세 대상 급여는 비과세 금액을 반영해 자동 계산됩니다.</p>
                      </div>
                    </div>
                    <span className="min-w-[92px] self-start whitespace-nowrap rounded-full bg-white px-3 py-1 text-center text-xs font-semibold text-slate-500 shadow-sm">
                      총급여 중심
                    </span>
                  </div>

                  <form className="grid grid-cols-1 gap-5 md:grid-cols-2" onSubmit={handlePrimarySave}>
                    <div className="space-y-2 md:col-span-2">
                      <label className="block text-sm font-semibold text-slate-700" htmlFor="payerName">현 근무지</label>
                      <input
                        className={`h-12 w-full rounded-lg border px-4 text-sm ${
                          isConfirmed
                            ? "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400"
                            : "border-slate-200 bg-white focus:border-primary focus:ring-primary"
                        }`}
                        disabled={isConfirmed}
                        id="payerName"
                        onChange={(event) => setPrimaryForm((current) => ({ ...current, payerName: event.target.value }))}
                        placeholder="비워두면 '현 근무지'로 저장됩니다."
                        type="text"
                        value={primaryForm.payerName}
                      />
                    </div>

                    <div className="space-y-2">
                      <label className="block text-sm font-semibold text-slate-700" htmlFor="totalSalary">총급여 (급여·상여 포함)</label>
                      <input
                        className={`h-12 w-full rounded-lg border px-4 text-right font-mono font-bold ${
                          isConfirmed
                            ? "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400"
                            : "border-slate-200 bg-white focus:border-primary focus:ring-primary"
                        }`}
                        disabled={isConfirmed}
                        id="totalSalary"
                        inputMode="numeric"
                        onChange={updatePrimaryMoneyField("totalSalary")}
                        type="text"
                        value={formatMoneyInput(primaryForm.totalSalary)}
                      />
                    </div>

                    <div className="rounded-xl border border-sky-200 bg-sky-50 px-4 py-3 text-xs leading-6 text-sky-700 md:col-span-2">
                      회사에서 받은 급여와 상여는 보통 이 총급여에 함께 포함합니다. 이미 총급여에 반영된 상여는 아래 예외 입력에서 다시 넣지 않습니다.
                    </div>

                    <div className="space-y-2">
                      <label className="block text-sm font-semibold text-slate-700" htmlFor="nonTaxableAmount">비과세 금액</label>
                      <input
                        className={`h-12 w-full rounded-lg border px-4 text-right font-mono font-bold ${
                          isConfirmed
                            ? "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400"
                            : "border-slate-200 bg-white focus:border-primary focus:ring-primary"
                        }`}
                        disabled={isConfirmed}
                        id="nonTaxableAmount"
                        inputMode="numeric"
                        onChange={updatePrimaryMoneyField("nonTaxableAmount")}
                        type="text"
                        value={formatMoneyInput(primaryForm.nonTaxableAmount)}
                      />
                    </div>

                    <div className="space-y-2">
                      <label className="block text-sm font-semibold text-slate-700" htmlFor="taxableSalary">자동 계산된 과세 대상 급여</label>
                      <input
                        className="h-12 w-full rounded-lg border border-slate-200 bg-slate-100 px-4 text-right font-mono font-bold text-slate-500"
                        disabled
                        id="taxableSalary"
                        type="text"
                        value={new Intl.NumberFormat("ko-KR").format(taxableSalary)}
                      />
                    </div>

                    <div className="space-y-2">
                      <label className="block text-sm font-semibold text-slate-700" htmlFor="withheldTaxAmount">총 기납부세액</label>
                      <input
                        className={`h-12 w-full rounded-lg border px-4 text-right font-mono font-bold ${
                          isConfirmed
                            ? "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400"
                            : "border-slate-200 bg-white focus:border-primary focus:ring-primary"
                        }`}
                        disabled={isConfirmed}
                        id="withheldTaxAmount"
                        inputMode="numeric"
                        onChange={updatePrimaryMoneyField("withheldTaxAmount")}
                        type="text"
                        value={formatMoneyInput(primaryForm.withheldTaxAmount)}
                      />
                    </div>

                    {!isConfirmed ? (
                      <div className="flex justify-end md:col-span-2">
                        <button className="rounded-xl bg-primary px-6 py-2.5 text-sm font-semibold text-white shadow-md transition-all hover:bg-primary/90 disabled:opacity-60" disabled={isSaving} type="submit">
                          {isSaving ? "저장 중..." : "기본 입력 저장"}
                        </button>
                      </div>
                    ) : null}
                  </form>
                </section>

                <section className="rounded-2xl border border-slate-200 bg-white p-6">
                  <div className="mb-6 flex items-start justify-between gap-4">
                    <div>
                      <h3 className="text-lg font-bold text-slate-900">예외 입력</h3>
                      <div className="mt-2 text-sm leading-5 text-slate-500">
                        <p>종전 근무지나 기타소득처럼 기본 입력만으로 처리하기 어려운 경우에만 사용하세요.</p>
                        <p>대부분의 근로소득자는 이 영역을 비워두어도 됩니다.</p>
                      </div>
                    </div>
                    {!isConfirmed ? (
                      <button
                        className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-600 transition hover:border-slate-300 hover:bg-slate-50"
                        onClick={openNewDetailForm}
                        type="button"
                      >
                        예외 항목 추가
                      </button>
                    ) : null}
                  </div>

                  <div className="mb-6 grid grid-cols-1 gap-3 md:grid-cols-2">
                    <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3">
                      <p className="text-sm font-semibold text-slate-900">종전 근무지가 있나요?</p>
                      <p className="mt-1 text-xs leading-6 text-slate-500">
                        올해 중간에 이직했다면, 종전 근무지에서 받은 총급여와 기납부세액을 예외 입력으로 추가하세요.
                      </p>
                    </div>
                    <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3">
                      <p className="text-sm font-semibold text-slate-900">기타소득이 있나요?</p>
                      <p className="mt-1 text-xs leading-6 text-slate-500">
                        강연료, 원고료, 사례금처럼 근로소득 외 별도 소득이 있을 때만 추가하세요. 비과세소득과는 다른 개념입니다.
                      </p>
                    </div>
                  </div>

                  <div className="grid grid-cols-1 gap-6 xl:grid-cols-[1fr_1.05fr]">
                    <div className="overflow-hidden rounded-xl border border-slate-200">
                      <div className="border-b border-slate-200 bg-slate-50 px-5 py-4">
                        <h4 className="font-semibold text-slate-900">등록된 예외 항목</h4>
                      </div>
                      <div className="divide-y divide-slate-100">
                        {additionalItems.length === 0 ? (
                          <div className="px-5 py-8 text-sm text-slate-500">
                            등록된 예외 항목이 없습니다. 일반적인 근로소득자는 기본 입력만으로 충분합니다.
                          </div>
                        ) : (
                          additionalItems.map((item) => (
                            <button
                              key={item.id}
                              className={`flex w-full items-center justify-between px-5 py-4 text-left transition ${
                                item.id === selectedDetailId
                                  ? "bg-sky-50 shadow-[inset_4px_0_0_0_rgb(125,211,252),inset_0_0_0_1px_rgba(125,211,252,0.45)]"
                                  : "hover:bg-slate-50"
                              }`}
                              onClick={() => selectDetailItem(item)}
                              type="button"
                            >
                              <div>
                                <p className="font-semibold text-slate-900">{getExceptionItemLabel(item)}</p>
                                <p className="mt-1 text-sm text-slate-500">{item.payerName || "-"}</p>
                              </div>
                              <div className="text-right">
                                <p className="font-mono text-sm font-bold text-slate-900">{formatCurrency(item.grossAmount)}</p>
                                <p className="mt-1 text-xs text-slate-400">기납부세액 {formatCurrency(item.withheldTaxAmount)}</p>
                              </div>
                            </button>
                          ))
                        )}
                      </div>
                    </div>

                    <div className="rounded-xl border border-slate-200 bg-slate-50/50 p-5">
                      <div className="mb-5 flex items-center justify-between gap-3">
                        <h4 className="font-semibold text-slate-900">
                          {selectedDetailId ? "예외 항목 수정" : "예외 항목 추가"}
                        </h4>
                        {selectedDetailId && !isConfirmed ? (
                          <button
                            className="rounded-lg border border-red-200 bg-red-50 px-3 py-1.5 text-xs font-semibold text-red-600 transition hover:bg-red-100"
                            onClick={handleDetailDelete}
                            type="button"
                          >
                            삭제
                          </button>
                        ) : null}
                      </div>

                      {isDetailOpen ? (
                        <form className="space-y-4" onSubmit={handleDetailSave}>
                          <div className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-xs leading-6 text-slate-500">
                            {isPreviousWorkplace
                              ? "이직 이력이 있다면 종전 근무지의 총급여와 기납부세액을 추가하세요."
                              : "강연료, 원고료, 사례금처럼 근로소득 외 별도 소득만 입력하세요."}
                          </div>

                          <div className="space-y-2">
                            <label className="block text-sm font-semibold text-slate-700" htmlFor="detail-incomeType">예외 구분</label>
                            <select
                              className={`h-12 w-full rounded-lg border px-4 text-sm ${
                                isConfirmed
                                  ? "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400"
                                  : "border-slate-200 bg-white focus:border-primary focus:ring-primary"
                              }`}
                              disabled={isConfirmed}
                              id="detail-incomeType"
                              onChange={(event) => setDetailForm((current) => ({ ...current, incomeType: event.target.value }))}
                              value={detailForm.incomeType}
                            >
                              {DETAIL_TYPE_OPTIONS.map((option) => (
                                <option key={option.value} value={option.value}>{option.label}</option>
                              ))}
                            </select>
                          </div>

                          <div className="space-y-2">
                            <label className="block text-sm font-semibold text-slate-700" htmlFor="detail-payerName">{detailPayerLabel}</label>
                            <input
                              className={`h-12 w-full rounded-lg border px-4 text-sm ${
                                isConfirmed
                                  ? "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400"
                                  : "border-slate-200 bg-white focus:border-primary focus:ring-primary"
                              }`}
                              disabled={isConfirmed}
                              id="detail-payerName"
                              onChange={(event) => setDetailForm((current) => ({ ...current, payerName: event.target.value }))}
                              type="text"
                              value={detailForm.payerName}
                            />
                          </div>

                          {[
                            { key: "grossAmount", label: detailGrossLabel },
                            { key: "taxableAmount", label: detailTaxableLabel },
                            { key: "nonTaxableAmount", label: "비과세 금액" },
                            { key: "withheldTaxAmount", label: detailWithheldLabel }
                          ].map((field) => (
                            <div key={field.key} className="space-y-2">
                              <label className="block text-sm font-semibold text-slate-700" htmlFor={`detail-${field.key}`}>{field.label}</label>
                              <input
                                className={`h-12 w-full rounded-lg border px-4 text-right font-mono font-bold ${
                                  isConfirmed
                                    ? "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400"
                                    : "border-slate-200 bg-white focus:border-primary focus:ring-primary"
                                }`}
                                disabled={isConfirmed}
                                id={`detail-${field.key}`}
                                inputMode="numeric"
                                onChange={updateDetailMoneyField(field.key)}
                                type="text"
                                value={formatMoneyInput(detailForm[field.key])}
                              />
                            </div>
                          ))}

                          {!isConfirmed ? (
                            <div className="flex justify-end gap-3 pt-2">
                              <button
                                className="rounded-lg border border-slate-200 px-5 py-2.5 text-sm font-medium transition-colors hover:bg-slate-50"
                                onClick={() => setIsDetailOpen(false)}
                                type="button"
                              >
                                닫기
                              </button>
                              <button className="rounded-lg bg-primary px-6 py-2.5 text-sm font-semibold text-white shadow-md transition-all hover:bg-primary/90 disabled:opacity-60" disabled={isSaving} type="submit">
                                {isSaving ? "저장 중..." : selectedDetailId ? "상세 수정 저장" : "상세 항목 추가"}
                              </button>
                            </div>
                          ) : null}
                        </form>
                      ) : (
                        <div className="rounded-xl border border-dashed border-slate-200 bg-white px-4 py-6 text-sm text-slate-500">
                          필요할 때만 상세 소득을 펼쳐 입력할 수 있습니다.
                        </div>
                      )}
                    </div>
                  </div>
                </section>
              </div>

              <div className="mt-8 flex items-center justify-between border-t border-slate-100 pt-6">
                <button
                  className="rounded-xl border border-slate-200 bg-white px-6 py-2.5 text-sm font-semibold text-slate-600 transition-all hover:border-slate-300 hover:bg-slate-50 hover:text-slate-900"
                  onClick={() => startTransition(() => router.push("/"))}
                  type="button"
                >
                  대시보드
                </button>
                <button
                  className={`rounded-xl px-6 py-2.5 text-sm font-semibold text-white transition-all ${isConfirmed ? "bg-red-500 hover:bg-red-600" : "bg-primary hover:bg-primary/90"}`}
                  disabled={isSaving}
                  onClick={handleConfirmStep}
                  type="button"
                >
                  {isConfirmed ? "2단계 확정 풀기" : "2단계 확정"}
                </button>
              </div>
            </div>
          </section>
        </div>
      </main>
    </div>
  );
}
