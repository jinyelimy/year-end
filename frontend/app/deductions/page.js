"use client";

import Link from "next/link";
import { useEffect, useState, startTransition } from "react";
import { useRouter } from "next/navigation";

import {
  clearAuth,
  createDeductionItem,
  deleteDeductionItem,
  getAccessToken,
  initializeAuthenticatedContext,
  listDeductionItems,
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

function MessageBanner({ message }) {
  if (!message) {
    return null;
  }

  const tone = message.type === "success"
    ? "border-emerald-200 bg-emerald-50 text-emerald-700"
    : "border-red-200 bg-red-50 text-red-700";

  return <div className={`rounded-xl border px-4 py-3 text-sm ${tone}`}>{message.text}</div>;
}

export default function DeductionsPage() {
  const router = useRouter();
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [message, setMessage] = useState(null);
  const [session, setSession] = useState(null);
  const [deductionItems, setDeductionItems] = useState([]);
  const [selectedItemId, setSelectedItemId] = useState(null);
  const [form, setForm] = useState(INITIAL_FORM);

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
        const items = await listDeductionItems(context.currentSession.id);

        if (!active) {
          return;
        }

        setSession(context.currentSession);
        setDeductionItems(items);
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

  async function reloadItems(nextSelectedId = selectedItemId) {
    const items = await listDeductionItems(session.id);
    setDeductionItems(items);

    if (!nextSelectedId) {
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
    if (!session?.id) {
      return;
    }

    setIsSaving(true);
    setMessage(null);

    try {
      const payload = {
        deductionType: form.deductionType,
        dependentId: null,
        subType: form.subType.trim() || null,
        amount: Number(form.amount || 0),
        usedAt: form.usedAt || null,
        sourceName: form.sourceName.trim() || null,
        evidenceStatus: form.evidenceStatus,
        attributesJsonb: JSON.stringify({})
      };

      if (selectedItemId) {
        await updateDeductionItem(session.id, selectedItemId, payload);
        setMessage({ type: "success", text: "공제 항목을 수정했습니다." });
      } else {
        await createDeductionItem(session.id, payload);
        setMessage({ type: "success", text: "공제 항목을 추가했습니다." });
      }

      await reloadItems(selectedItemId);
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
    if (!session?.id || !selectedItemId) {
      return;
    }

    setIsSaving(true);
    setMessage(null);

    try {
      await deleteDeductionItem(session.id, selectedItemId);
      setMessage({ type: "success", text: "공제 항목을 삭제했습니다." });
      resetForm();
      await reloadItems(null);
    } catch (error) {
      setMessage({ type: "error", text: error.message || "공제 항목 삭제에 실패했습니다." });
    } finally {
      setIsSaving(false);
    }
  }

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background-light text-slate-500">
        공제 항목을 불러오는 중입니다...
      </div>
    );
  }

  const totalDeduction = deductionItems.reduce((sum, item) => sum + (Number(item.amount) || 0), 0);
  const groupedCounts = DEDUCTION_TYPE_OPTIONS.map((option) => ({
    ...option,
    count: deductionItems.filter((item) => item.deductionType === option.value).length,
    amount: deductionItems
      .filter((item) => item.deductionType === option.value)
      .reduce((sum, item) => sum + (Number(item.amount) || 0), 0)
  }));

  return (
    <div className="min-h-screen bg-background-light font-display text-slate-900">
      <div className="relative flex min-h-screen w-full flex-col overflow-x-hidden">
        <header className="sticky top-0 z-50 flex items-center justify-between border-b border-slate-200 bg-white px-4 py-3 md:px-10">
          <div className="flex items-center gap-4">
            <div className="text-primary">
              <span className="material-symbols-outlined text-3xl">account_balance_wallet</span>
            </div>
            <h2 className="text-lg font-bold tracking-tight">연말정산</h2>
          </div>
          <div className="flex gap-2">
            <button
              className="flex items-center justify-center rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-50"
              onClick={resetForm}
              type="button"
            >
              새 항목
            </button>
            <Link className="flex items-center justify-center gap-2 rounded-xl bg-primary px-6 py-3 font-bold text-white shadow-lg shadow-primary/20 transition-colors hover:bg-primary/90" href="/results">
              <span>결과 보기</span>
              <span className="material-symbols-outlined">arrow_forward</span>
            </Link>
          </div>
        </header>

        <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col p-4 md:p-8">
          <div className="mb-8 flex flex-col justify-between gap-6 md:flex-row md:items-end">
            <div className="flex flex-col gap-2">
              <nav className="mb-2 flex gap-2 text-sm text-slate-500">
                <Link href="/">홈</Link>
                <span>&gt;</span>
                <span className="font-medium text-primary">공제 입력</span>
              </nav>
              <h1 className="text-3xl font-black tracking-tight md:text-4xl">지출 및 공제 항목 입력</h1>
              <p className="text-lg text-slate-600">카테고리별 공제 내역을 확인하고 직접 조정할 수 있습니다.</p>
            </div>
            <div className="rounded-2xl border border-primary/20 bg-primary/5 px-6 py-4 text-right">
              <p className="text-sm font-semibold text-slate-500">총 공제 금액</p>
              <p className="mt-1 text-3xl font-black text-primary">{formatCurrency(totalDeduction)}</p>
            </div>
          </div>

          <div className="mb-8">
            <MessageBanner message={message} />
          </div>

          <h2 className="mb-6 flex items-center gap-2 text-xl font-bold">
            <span className="material-symbols-outlined text-primary">category</span>
            항목별 요약
          </h2>
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {groupedCounts.map((group) => (
              <button
                key={group.value}
                className="group rounded-2xl border border-slate-200 bg-white p-6 text-left transition-all hover:border-primary/50"
                onClick={() => setForm((current) => ({ ...current, deductionType: group.value }))}
                type="button"
              >
                <div className="mb-4 flex items-start justify-between">
                  <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10 text-primary">
                    <span className="material-symbols-outlined text-3xl">receipt_long</span>
                  </div>
                  <span className="material-symbols-outlined text-slate-300 transition-colors group-hover:text-primary">arrow_forward_ios</span>
                </div>
                <h3 className="mb-1 text-lg font-bold">{group.label}</h3>
                <p className="mb-4 text-sm text-slate-500">등록 {group.count}건</p>
                <div className="mt-auto border-t border-slate-100 pt-4">
                  <p className="text-xs font-bold uppercase tracking-wider text-slate-400">누적 금액</p>
                  <p className="text-xl font-black text-primary">{formatCurrency(group.amount)}</p>
                </div>
              </button>
            ))}
          </div>

          <div className="mt-10 grid grid-cols-1 gap-8 xl:grid-cols-[1.1fr_0.9fr]">
            <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
              <div className="mb-4 flex items-center justify-between">
                <h3 className="text-lg font-bold">등록된 공제 항목</h3>
                <span className="rounded bg-slate-100 px-2 py-1 text-xs font-semibold text-slate-500">
                  총 {deductionItems.length}건
                </span>
              </div>
              <div className="space-y-3">
                {deductionItems.length === 0 ? (
                  <div className="rounded-xl border border-dashed border-slate-200 bg-slate-50 p-6 text-sm text-slate-500">
                    아직 공제 항목이 없습니다. 오른쪽 폼에서 첫 항목을 추가해 주세요.
                  </div>
                ) : (
                  deductionItems.map((item) => {
                    const active = item.id === selectedItemId;

                    return (
                      <button
                        key={item.id}
                        className={`flex w-full items-center justify-between rounded-xl border p-4 text-left transition-all ${
                          active
                            ? "border-primary bg-primary/5"
                            : "border-slate-200 bg-white hover:border-primary/40"
                        }`}
                        onClick={() => selectItem(item)}
                        type="button"
                      >
                        <div>
                          <p className="font-bold text-slate-900">{getDeductionTypeLabel(item.deductionType)}</p>
                          <p className="mt-1 text-sm text-slate-500">
                            {item.sourceName || "출처 미입력"} {item.usedAt ? `· ${item.usedAt}` : ""}
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
                  })
                )}
              </div>
            </div>

            <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
              <h3 className="mb-6 text-lg font-bold">{selectedItemId ? "공제 항목 수정" : "공제 항목 추가"}</h3>
              <form className="space-y-5" onSubmit={handleSubmit}>
                <div className="space-y-2">
                  <label className="block text-sm font-semibold text-slate-700" htmlFor="deduction-type">
                    공제 구분
                  </label>
                  <select
                    className="h-12 w-full rounded-lg border-slate-200 bg-slate-50 text-sm focus:border-primary focus:ring-primary"
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
                    className="h-12 w-full rounded-lg border-slate-200 bg-white px-4 text-sm focus:border-primary focus:ring-primary"
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
                  <div className="relative">
                    <input
                      className="h-12 w-full rounded-lg border-slate-200 bg-white px-4 pr-10 text-right font-mono font-bold focus:border-primary focus:ring-primary"
                      id="amount"
                      min="0"
                      onChange={(event) => setForm((current) => ({ ...current, amount: event.target.value }))}
                      step="1"
                      type="number"
                      value={form.amount}
                    />
                    <span className="absolute right-3 top-1/2 -translate-y-1/2 text-sm text-slate-400">원</span>
                  </div>
                </div>

                <div className="space-y-2">
                  <label className="block text-sm font-semibold text-slate-700" htmlFor="used-at">
                    사용일
                  </label>
                  <input
                    className="h-12 w-full rounded-lg border-slate-200 bg-white px-4 text-sm focus:border-primary focus:ring-primary"
                    id="used-at"
                    onChange={(event) => setForm((current) => ({ ...current, usedAt: event.target.value }))}
                    type="date"
                    value={form.usedAt}
                  />
                </div>

                <div className="space-y-2">
                  <label className="block text-sm font-semibold text-slate-700" htmlFor="source-name">
                    출처
                  </label>
                  <input
                    className="h-12 w-full rounded-lg border-slate-200 bg-white px-4 text-sm focus:border-primary focus:ring-primary"
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
                    className="h-12 w-full rounded-lg border-slate-200 bg-slate-50 text-sm focus:border-primary focus:ring-primary"
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

                <div className="flex justify-end gap-3 pt-2">
                  {selectedItemId ? (
                    <button
                      className="rounded-lg border border-red-200 px-5 py-2.5 text-sm font-semibold text-red-500 transition-colors hover:bg-red-50"
                      disabled={isSaving}
                      onClick={handleDelete}
                      type="button"
                    >
                      삭제
                    </button>
                  ) : null}
                  <button
                    className="rounded-lg border border-slate-200 px-5 py-2.5 text-sm font-medium transition-colors hover:bg-slate-50"
                    onClick={resetForm}
                    type="button"
                  >
                    초기화
                  </button>
                  <button
                    className="rounded-lg bg-primary px-6 py-2.5 text-sm font-semibold text-white shadow-md transition-all hover:bg-primary/90 disabled:opacity-60"
                    disabled={isSaving}
                    type="submit"
                  >
                    {isSaving ? "저장 중..." : selectedItemId ? "수정 저장" : "항목 추가"}
                  </button>
                </div>
              </form>
            </div>
          </div>

          <div className="mt-10 flex items-center justify-between border-t border-slate-200 pt-8">
            <Link className="flex items-center gap-2 text-slate-600 hover:text-primary" href="/income">
              <span className="material-symbols-outlined">arrow_back</span>
              <span>이전: 소득 명세</span>
            </Link>
            <Link className="flex items-center gap-2 rounded-xl bg-primary px-6 py-3 font-bold text-white shadow-lg shadow-primary/20 transition-transform hover:scale-[1.02]" href="/results">
              <span>다음: 결과 확인</span>
              <span className="material-symbols-outlined">arrow_forward</span>
            </Link>
          </div>
        </main>
      </div>
    </div>
  );
}
