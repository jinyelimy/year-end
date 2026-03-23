"use client";

import Link from "next/link";
import { useEffect, useMemo, useState, startTransition } from "react";
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
  DEDUCTION_TYPE_OPTIONS,
  EVIDENCE_STATUS_OPTIONS,
  formatCurrency,
  getDeductionTypeLabel,
  getEvidenceStatusLabel
} from "@/lib/yearEndView";

const INITIAL_FORM = {
  deductionType: "MEDICAL_EXPENSE",
  subType: "",
  amount: "0",
  usedAt: "",
  sourceName: "",
  evidenceStatus: "PENDING"
};

function parseDeductionAttributes(item) {
  if (!item?.attributesJsonb) {
    return {};
  }

  try {
    return JSON.parse(item.attributesJsonb);
  } catch {
    return {};
  }
}

function getDeductionSource(item) {
  const attributes = parseDeductionAttributes(item);

  if (attributes.sourceType === "HOMETAX") {
    return {
      type: "HOMETAX",
      label: attributes.sourceLabel || "간소화자료",
      badgeClass: "bg-sky-100 text-sky-700"
    };
  }

  return {
    type: "MANUAL",
    label: attributes.sourceLabel || "직접입력",
    badgeClass: "bg-slate-100 text-slate-600"
  };
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

function validateForm(form) {
  if (toNumber(form.amount) < 0) {
    return "금액은 0원 이상이어야 합니다.";
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

export default function DeductionsPage() {
  const router = useRouter();
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [message, setMessage] = useState(null);
  const [user, setUser] = useState(null);
  const [session, setSession] = useState(null);
  const [deductionItems, setDeductionItems] = useState([]);
  const [selectedItemId, setSelectedItemId] = useState(null);
  const [form, setForm] = useState(INITIAL_FORM);

  const isConfirmed = hasDeductionsConfirmed(session);

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
        if (!hasIncomeConfirmed(context.currentSession)) {
          startTransition(() => {
            router.replace("/income");
          });
          return;
        }

        const items = await listDeductionItems(context.currentSession.id);
        if (!active) {
          return;
        }

        setUser(context.user);
        setSession(context.currentSession);
        setDeductionItems(items);
        if (items[0]) {
          selectItem(items[0]);
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

  function resetForm() {
    setSelectedItemId(null);
    setForm(INITIAL_FORM);
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
    if (nextSelected) {
      selectItem(nextSelected);
    } else {
      resetForm();
    }
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (!session?.id || isConfirmed) {
      return;
    }

    const validationError = validateForm(form);
    if (validationError) {
      setMessage({ type: "error", text: validationError });
      return;
    }

    setIsSaving(true);
    setMessage(null);

    try {
      const currentItem = deductionItems.find((item) => item.id === selectedItemId);
      const currentAttributes = currentItem ? parseDeductionAttributes(currentItem) : {};
      const sourceType = currentAttributes.sourceType === "HOMETAX" ? "HOMETAX" : "MANUAL";
      const sourceLabel = currentAttributes.sourceLabel || (sourceType === "HOMETAX" ? "간소화자료" : "직접입력");

      const payload = {
        deductionType: form.deductionType,
        dependentId: null,
        subType: form.subType.trim() || null,
        amount: toNumber(form.amount),
        usedAt: form.usedAt || null,
        sourceName: form.sourceName.trim() || null,
        evidenceStatus: form.evidenceStatus,
        attributesJsonb: JSON.stringify({
          sourceType,
          sourceLabel,
          entryChannel: sourceType === "HOMETAX" ? "IMPORT_SYNC" : "MANUAL_FORM"
        })
      };

      if (selectedItemId) {
        await updateDeductionItem(session.id, selectedItemId, payload);
        setMessage({ type: "success", text: "공제 항목을 수정했습니다." });
        await reloadItems(selectedItemId);
      } else {
        await createDeductionItem(session.id, payload);
        setMessage({ type: "success", text: "공제 항목을 추가했습니다." });
        await reloadItems(null);
      }

      await syncDeductionsConfirmation(false);
      if (!selectedItemId) {
        resetForm();
      }
    } catch (error) {
      setMessage({ type: "error", text: error.message || "공제 항목 저장에 실패했습니다." });
    } finally {
      setIsSaving(false);
    }
  }

  async function handleDelete() {
    if (!session?.id || !selectedItemId || isConfirmed) {
      return;
    }

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

  async function handleConfirmStep() {
    if (!session?.id) {
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
        window.setTimeout(() => {
          startTransition(() => {
            router.replace("/");
          });
        }, 250);
      }
    } catch (error) {
      setMessage({ type: "error", text: error.message || "3단계 확정 처리에 실패했습니다." });
    } finally {
      setIsSaving(false);
    }
  }

  const groupedCounts = useMemo(
    () =>
      DEDUCTION_TYPE_OPTIONS.map((option) => ({
        ...option,
        count: deductionItems.filter((item) => item.deductionType === option.value).length,
        amount: deductionItems
          .filter((item) => item.deductionType === option.value)
          .reduce((sum, item) => sum + (Number(item.amount) || 0), 0)
      })),
    [deductionItems]
  );

  const importedCount = deductionItems.filter((item) => getDeductionSource(item).type === "HOMETAX").length;
  const manualCount = deductionItems.length - importedCount;
  const totalDeduction = deductionItems.reduce((sum, item) => sum + (Number(item.amount) || 0), 0);

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background-light text-slate-500">
        공제 항목을 불러오는 중입니다...
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background-light font-display text-slate-900">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-4 sm:px-6 lg:px-8">
          <Link className="flex items-center gap-3 text-primary" href="/">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
              <span className="material-symbols-outlined">receipt_long</span>
            </div>
            <span className="text-lg font-bold tracking-tight text-slate-900">Ligg-Tax</span>
          </Link>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 gap-8 lg:grid-cols-12">
          <aside className="lg:col-span-3">
            <StageThreeSidebar
              activeStep="deductions"
              importedCount={importedCount}
              isConfirmed={isConfirmed}
              manualCount={manualCount}
              session={session}
              user={user}
            />
          </aside>

          <section className="lg:col-span-9">
            <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm md:p-8">
              <div className="mb-8 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
                <div>
                  <h1 className="text-2xl font-bold text-slate-900">공제 항목 입력</h1>
                  <p className="mt-2 text-sm leading-6 text-slate-500">
                    간소화 자료에서 넘어온 항목은 출처 배지로 구분하고, 필요한 공제는 직접 추가할 수 있습니다.
                    3단계가 확정되기 전까지는 계속 수정할 수 있습니다.
                  </p>
                </div>
                <div className="rounded-2xl border border-primary/20 bg-primary/5 px-6 py-4 text-right">
                  <p className="text-sm font-semibold text-slate-500">총 공제 금액</p>
                  <p className="mt-1 text-3xl font-black text-primary">{formatCurrency(totalDeduction)}</p>
                </div>
              </div>

              {isConfirmed ? (
                <div className="mb-6 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-800">
                  3단계가 확정되어 현재 입력이 잠겨 있습니다. 수정이 필요하면 아래의 `3단계 확정 되돌리기`
                  버튼을 눌러 주세요.
                </div>
              ) : null}

              <div className="mb-6 rounded-2xl border border-sky-200 bg-sky-50 px-5 py-4 text-sm leading-6 text-sky-800">
                현재 화면은 실제로 연동된 5개 공제 항목을 기준으로 동작합니다. 주택자금, 연금계좌,
                저축 등 확장 카테고리는 다음 범위에서 이어서 추가할 예정입니다.
              </div>

              <MessageBanner message={message} />

              <h2 className="mb-4 text-lg font-bold text-slate-900">현재 지원 카테고리</h2>
              <div className="mb-10 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {groupedCounts.map((group) => (
                  <button
                    key={group.value}
                    className="rounded-2xl border border-slate-200 bg-white p-5 text-left transition hover:border-primary/40"
                    onClick={() => setForm((current) => ({ ...current, deductionType: group.value }))}
                    type="button"
                  >
                    <div className="mb-4 flex h-11 w-11 items-center justify-center rounded-xl bg-primary/10 text-primary">
                      <span className="material-symbols-outlined">receipt_long</span>
                    </div>
                    <h3 className="text-base font-bold text-slate-900">{group.label}</h3>
                    <p className="mt-1 text-sm text-slate-500">등록 {group.count}건</p>
                    <p className="mt-4 text-xl font-black text-primary">{formatCurrency(group.amount)}</p>
                  </button>
                ))}
              </div>

              <div className="grid grid-cols-1 gap-6 xl:grid-cols-[1.05fr_0.95fr]">
                <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
                  <div className="mb-4 flex items-center justify-between">
                    <h2 className="text-lg font-bold text-slate-900">등록된 공제 항목</h2>
                    <button
                      className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
                      disabled={isConfirmed}
                      onClick={resetForm}
                      type="button"
                    >
                      새 항목 추가
                    </button>
                  </div>

                  {deductionItems.length === 0 ? (
                    <div className="rounded-xl border border-dashed border-slate-200 bg-slate-50 px-4 py-8 text-sm text-slate-500">
                      아직 공제 항목이 없습니다. 오른쪽 입력 폼에서 첫 항목을 추가해 주세요.
                    </div>
                  ) : (
                    <div className="space-y-3">
                      {deductionItems.map((item) => {
                        const source = getDeductionSource(item);
                        const active = item.id === selectedItemId;

                        return (
                          <button
                            key={item.id}
                            className={`flex w-full items-center justify-between rounded-xl px-4 py-4 text-left transition ${
                              active
                                ? "bg-sky-50 shadow-[inset_4px_0_0_0_rgb(125,211,252),inset_0_0_0_1px_rgba(125,211,252,0.45)]"
                                : "border border-slate-200 bg-white hover:border-primary/40"
                            }`}
                            onClick={() => selectItem(item)}
                            type="button"
                          >
                            <div>
                              <div className="flex flex-wrap items-center gap-2">
                                <p className="font-semibold text-slate-900">
                                  {getDeductionTypeLabel(item.deductionType)}
                                </p>
                                <span className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${source.badgeClass}`}>
                                  {source.label}
                                </span>
                              </div>
                              <p className="mt-1 text-sm text-slate-500">
                                {item.sourceName || "출처 미입력"}
                                {item.usedAt ? ` · ${item.usedAt}` : ""}
                              </p>
                            </div>
                            <div className="text-right">
                              <p className="font-bold text-primary">{formatCurrency(item.amount)}</p>
                              <span className="mt-2 inline-flex rounded-full bg-slate-100 px-2 py-1 text-[11px] font-semibold text-slate-600">
                                {getEvidenceStatusLabel(item.evidenceStatus)}
                              </span>
                            </div>
                          </button>
                        );
                      })}
                    </div>
                  )}
                </div>

                <div className="rounded-2xl border border-slate-200 bg-slate-50/60 p-6">
                  <div className="mb-5 flex items-center justify-between">
                    <h2 className="text-lg font-bold text-slate-900">
                      {selectedItemId ? "공제 항목 수정" : "공제 항목 추가"}
                    </h2>
                    {selectedItemId ? (
                      <button
                        className="rounded-lg border border-red-200 bg-red-50 px-3 py-1.5 text-xs font-semibold text-red-600 transition hover:bg-red-100 disabled:opacity-60"
                        disabled={isConfirmed || isSaving}
                        onClick={handleDelete}
                        type="button"
                      >
                        삭제
                      </button>
                    ) : null}
                  </div>

                  <form className="space-y-4" onSubmit={handleSubmit}>
                    <div className="space-y-2">
                      <label className="block text-sm font-semibold text-slate-700" htmlFor="deduction-type">
                        공제 구분
                      </label>
                      <select
                        className={`h-12 w-full rounded-lg border px-4 text-sm ${
                          isConfirmed
                            ? "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400"
                            : "border-slate-200 bg-white focus:border-primary focus:ring-primary"
                        }`}
                        disabled={isConfirmed}
                        id="deduction-type"
                        onChange={(event) => setForm((current) => ({ ...current, deductionType: event.target.value }))}
                        value={form.deductionType}
                      >
                        {DEDUCTION_TYPE_OPTIONS.map((option) => (
                          <option key={option.value} value={option.value}>
                            {option.label}
                          </option>
                        ))}
                      </select>
                    </div>

                    <div className="space-y-2">
                      <label className="block text-sm font-semibold text-slate-700" htmlFor="sub-type">
                        세부 구분
                      </label>
                      <input
                        className={`h-12 w-full rounded-lg border px-4 text-sm ${
                          isConfirmed
                            ? "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400"
                            : "border-slate-200 bg-white focus:border-primary focus:ring-primary"
                        }`}
                        disabled={isConfirmed}
                        id="sub-type"
                        onChange={(event) => setForm((current) => ({ ...current, subType: event.target.value }))}
                        type="text"
                        value={form.subType}
                      />
                    </div>

                    <div className="space-y-2">
                      <label className="block text-sm font-semibold text-slate-700" htmlFor="amount">
                        금액
                      </label>
                      <input
                        className={`h-12 w-full rounded-lg border px-4 text-right font-mono font-bold ${
                          isConfirmed
                            ? "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400"
                            : "border-slate-200 bg-white focus:border-primary focus:ring-primary"
                        }`}
                        disabled={isConfirmed}
                        id="amount"
                        inputMode="numeric"
                        onChange={(event) => setForm((current) => ({ ...current, amount: sanitizeMoneyInput(event.target.value) }))}
                        type="text"
                        value={formatMoneyInput(form.amount)}
                      />
                    </div>

                    <div className="space-y-2">
                      <label className="block text-sm font-semibold text-slate-700" htmlFor="used-at">
                        사용일
                      </label>
                      <input
                        className={`h-12 w-full rounded-lg border px-4 text-sm ${
                          isConfirmed
                            ? "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400"
                            : "border-slate-200 bg-white focus:border-primary focus:ring-primary"
                        }`}
                        disabled={isConfirmed}
                        id="used-at"
                        onChange={(event) => setForm((current) => ({ ...current, usedAt: event.target.value }))}
                        type="date"
                        value={form.usedAt}
                      />
                    </div>

                    <div className="space-y-2">
                      <label className="block text-sm font-semibold text-slate-700" htmlFor="source-name">
                        사용처 / 출처
                      </label>
                      <input
                        className={`h-12 w-full rounded-lg border px-4 text-sm ${
                          isConfirmed
                            ? "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400"
                            : "border-slate-200 bg-white focus:border-primary focus:ring-primary"
                        }`}
                        disabled={isConfirmed}
                        id="source-name"
                        onChange={(event) => setForm((current) => ({ ...current, sourceName: event.target.value }))}
                        type="text"
                        value={form.sourceName}
                      />
                    </div>

                    <div className="space-y-2">
                      <label className="block text-sm font-semibold text-slate-700" htmlFor="evidence-status">
                        증빙 상태
                      </label>
                      <select
                        className={`h-12 w-full rounded-lg border px-4 text-sm ${
                          isConfirmed
                            ? "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400"
                            : "border-slate-200 bg-white focus:border-primary focus:ring-primary"
                        }`}
                        disabled={isConfirmed}
                        id="evidence-status"
                        onChange={(event) => setForm((current) => ({ ...current, evidenceStatus: event.target.value }))}
                        value={form.evidenceStatus}
                      >
                        {EVIDENCE_STATUS_OPTIONS.map((option) => (
                          <option key={option.value} value={option.value}>
                            {option.label}
                          </option>
                        ))}
                      </select>
                    </div>

                    {!isConfirmed ? (
                      <div className="flex justify-end gap-3 pt-2">
                        <button
                          className="rounded-lg border border-slate-200 px-5 py-2.5 text-sm font-medium transition hover:bg-slate-50"
                          onClick={resetForm}
                          type="button"
                        >
                          초기화
                        </button>
                        <button
                          className="rounded-lg bg-primary px-6 py-2.5 text-sm font-semibold text-white shadow-md transition hover:bg-primary/90 disabled:opacity-60"
                          disabled={isSaving}
                          type="submit"
                        >
                          {isSaving ? "저장 중..." : selectedItemId ? "공제 수정 저장" : "공제 항목 추가"}
                        </button>
                      </div>
                    ) : null}
                  </form>
                </div>
              </div>

              <div className="mt-8 flex items-center justify-between border-t border-slate-100 pt-6">
                <button
                  className="rounded-xl border border-slate-200 bg-white px-6 py-2.5 text-sm font-semibold text-slate-600 transition hover:border-slate-300 hover:bg-slate-50"
                  onClick={() => startTransition(() => router.push("/"))}
                  type="button"
                >
                  대시보드
                </button>
                <button
                  className={`rounded-xl px-6 py-2.5 text-sm font-semibold text-white transition ${
                    isConfirmed ? "bg-red-500 hover:bg-red-600" : "bg-primary hover:bg-primary/90"
                  }`}
                  disabled={isSaving}
                  onClick={handleConfirmStep}
                  type="button"
                >
                  {isConfirmed ? "3단계 확정 되돌리기" : "3단계 확정"}
                </button>
              </div>
            </div>
          </section>
        </div>
      </main>
    </div>
  );
}
