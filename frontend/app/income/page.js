"use client";

import Link from "next/link";
import { useEffect, useState, startTransition } from "react";
import { useRouter } from "next/navigation";

import {
  clearAuth,
  createIncomeItem,
  getAccessToken,
  initializeAuthenticatedContext,
  listIncomeItems,
  updateIncomeItem,
  deleteIncomeItem
} from "@/lib/yearEndApi";
import {
  calculateFinancialSummary,
  formatCurrency,
  getIncomeTypeLabel,
  INCOME_TYPE_OPTIONS
} from "@/lib/yearEndView";

const INITIAL_FORM = {
  incomeType: "SALARY",
  payerName: "",
  grossAmount: "0",
  taxableAmount: "0",
  withheldTaxAmount: "0",
  nonTaxableAmount: "0"
};

function MessageBanner({ message }) {
  if (!message) {
    return null;
  }

  const tone = message.type === "success"
    ? "border-emerald-200 bg-emerald-50 text-emerald-700"
    : "border-red-200 bg-red-50 text-red-700";

  return <div className={`mb-6 rounded-xl border px-4 py-3 text-sm ${tone}`}>{message.text}</div>;
}

export default function IncomePage() {
  const router = useRouter();
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [message, setMessage] = useState(null);
  const [session, setSession] = useState(null);
  const [incomeItems, setIncomeItems] = useState([]);
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
        const items = await listIncomeItems(context.currentSession.id);

        if (!active) {
          return;
        }

        setSession(context.currentSession);
        setIncomeItems(items);
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
      incomeType: item.incomeType || "SALARY",
      payerName: item.payerName || "",
      grossAmount: String(item.grossAmount ?? 0),
      taxableAmount: String(item.taxableAmount ?? 0),
      withheldTaxAmount: String(item.withheldTaxAmount ?? 0),
      nonTaxableAmount: String(item.nonTaxableAmount ?? 0)
    });
  }

  async function reloadItems(nextSelectedId = selectedItemId) {
    const items = await listIncomeItems(session.id);
    setIncomeItems(items);

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
        incomeType: form.incomeType,
        payerName: form.payerName.trim(),
        grossAmount: Number(form.grossAmount || 0),
        taxableAmount: Number(form.taxableAmount || 0),
        withheldTaxAmount: Number(form.withheldTaxAmount || 0),
        nonTaxableAmount: Number(form.nonTaxableAmount || 0),
        attributesJsonb: JSON.stringify({})
      };

      if (selectedItemId) {
        await updateIncomeItem(session.id, selectedItemId, payload);
        setMessage({ type: "success", text: "소득 항목을 수정했습니다." });
      } else {
        await createIncomeItem(session.id, payload);
        setMessage({ type: "success", text: "소득 항목을 추가했습니다." });
      }

      await reloadItems(selectedItemId);
      if (!selectedItemId) {
        resetForm();
      }
    } catch (error) {
      setMessage({ type: "error", text: error.message || "소득 항목 저장에 실패했습니다." });
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
      await deleteIncomeItem(session.id, selectedItemId);
      setMessage({ type: "success", text: "소득 항목을 삭제했습니다." });
      resetForm();
      await reloadItems(null);
    } catch (error) {
      setMessage({ type: "error", text: error.message || "소득 항목 삭제에 실패했습니다." });
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

  const summary = calculateFinancialSummary(incomeItems, []);

  return (
    <div className="min-h-screen bg-background-light font-display text-slate-900">
      <div className="relative flex min-h-screen w-full overflow-hidden">
        <aside className="flex h-auto w-64 flex-col border-r border-slate-200 bg-white">
          <div className="p-6">
            <h1 className="text-xl font-bold text-primary">연말정산</h1>
            <p className="mt-1 text-xs text-slate-500">{session?.taxYear}년 귀속 정산 흐름</p>
          </div>
          <nav className="flex-1 space-y-1 px-4">
            <Link className="flex items-center gap-3 rounded-lg px-3 py-2 text-slate-600 transition-colors hover:bg-slate-50" href="/">
              <span className="material-symbols-outlined text-[22px]">dashboard</span>
              <span className="text-sm font-medium">대시보드</span>
            </Link>
            <Link className="flex items-center gap-3 rounded-lg bg-primary/10 px-3 py-2 text-primary transition-colors" href="/income">
              <span className="material-symbols-outlined text-[22px]">database</span>
              <span className="text-sm font-semibold">소득명세</span>
            </Link>
            <Link className="flex items-center gap-3 rounded-lg px-3 py-2 text-slate-600 transition-colors hover:bg-slate-50" href="/deductions">
              <span className="material-symbols-outlined text-[22px]">receipt_long</span>
              <span className="text-sm font-medium">공제 입력</span>
            </Link>
            <Link className="flex items-center gap-3 rounded-lg px-3 py-2 text-slate-600 transition-colors hover:bg-slate-50" href="/results">
              <span className="material-symbols-outlined text-[22px]">description</span>
              <span className="text-sm font-medium">결과 확인</span>
            </Link>
          </nav>
        </aside>

        <main className="flex flex-1 flex-col overflow-y-auto">
          <header className="sticky top-0 z-10 flex h-16 items-center justify-between border-b border-slate-200 bg-white/80 px-8 backdrop-blur-md">
            <div className="flex items-center gap-2 text-sm text-slate-500">
              <span>연말정산</span>
              <span className="material-symbols-outlined text-xs">chevron_right</span>
              <span className="font-medium text-slate-900">소득 명세 확인</span>
            </div>
            <div className="flex gap-2">
              <button
                className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100"
                onClick={resetForm}
                type="button"
              >
                새 항목
              </button>
              <Link className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white transition-opacity hover:bg-primary/90" href="/deductions">
                다음 단계
              </Link>
            </div>
          </header>

          <div className="mx-auto w-full max-w-6xl p-8">
            <div className="mb-8">
              <h2 className="text-3xl font-bold tracking-tight text-slate-900">소득 명세 확인</h2>
              <p className="mt-2 text-slate-600">
                등록된 급여 및 기타 소득 내역을 확인하고 필요하면 수정하세요.
              </p>
            </div>

            <div className="mb-8 grid grid-cols-1 gap-4 md:grid-cols-3">
              <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">총 지급액</p>
                <p className="mt-3 text-2xl font-black text-slate-900">{formatCurrency(summary.totalGrossIncome)}</p>
              </div>
              <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">과세 소득</p>
                <p className="mt-3 text-2xl font-black text-primary">{formatCurrency(summary.totalTaxableIncome)}</p>
              </div>
              <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">원천징수세액</p>
                <p className="mt-3 text-2xl font-black text-slate-900">{formatCurrency(summary.totalWithheldTax)}</p>
              </div>
            </div>

            <MessageBanner message={message} />

            <div className="grid grid-cols-1 gap-8 xl:grid-cols-[1.35fr_0.9fr]">
              <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
                <div className="flex items-center justify-between border-b border-slate-200 p-6">
                  <h3 className="text-lg font-bold">등록된 소득 내역</h3>
                  <span className="rounded bg-slate-100 px-2 py-1 text-xs font-medium tracking-wide text-slate-500">
                    총 {incomeItems.length}건
                  </span>
                </div>
                <div className="overflow-x-auto">
                  <table className="w-full border-collapse text-left">
                    <thead>
                      <tr className="bg-slate-50/50 text-xs font-semibold uppercase text-slate-500">
                        <th className="px-6 py-4">구분</th>
                        <th className="px-6 py-4">지급처</th>
                        <th className="px-6 py-4 text-right">총액</th>
                        <th className="px-6 py-4 text-right">원천세</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {incomeItems.length === 0 ? (
                        <tr>
                          <td className="px-6 py-8 text-sm text-slate-500" colSpan="4">
                            아직 등록된 소득 항목이 없습니다. 오른쪽 폼에서 첫 항목을 추가해 주세요.
                          </td>
                        </tr>
                      ) : (
                        incomeItems.map((item) => {
                          const active = item.id === selectedItemId;

                          return (
                            <tr
                              key={item.id}
                              className={`cursor-pointer transition-colors hover:bg-slate-50/80 ${active ? "bg-primary/5" : ""}`}
                              onClick={() => selectItem(item)}
                            >
                              <td className="px-6 py-5 font-semibold text-slate-900">{getIncomeTypeLabel(item.incomeType)}</td>
                              <td className="px-6 py-5 text-sm text-slate-500">{item.payerName || "-"}</td>
                              <td className="px-6 py-5 text-right font-mono text-lg font-bold tracking-tight text-slate-900">
                                {new Intl.NumberFormat("ko-KR").format(item.grossAmount || 0)}
                              </td>
                              <td className="px-6 py-5 text-right font-semibold text-primary">
                                {formatCurrency(item.withheldTaxAmount)}
                              </td>
                            </tr>
                          );
                        })
                      )}
                    </tbody>
                  </table>
                </div>
              </div>

              <div className="rounded-xl border border-slate-200 bg-white p-8 shadow-sm">
                <h3 className="mb-6 flex items-center gap-2 text-lg font-bold">
                  <span className="material-symbols-outlined text-primary">edit_note</span>
                  {selectedItemId ? "소득 항목 수정" : "소득 항목 추가"}
                </h3>

                <form className="space-y-5" onSubmit={handleSubmit}>
                  <div className="space-y-2">
                    <label className="block text-sm font-semibold text-slate-700" htmlFor="income-type">
                      소득 구분
                    </label>
                    <select
                      className="h-12 w-full rounded-lg border-slate-200 bg-slate-50 text-sm focus:border-primary focus:ring-primary"
                      id="income-type"
                      onChange={(event) => setForm((current) => ({ ...current, incomeType: event.target.value }))}
                      value={form.incomeType}
                    >
                      {INCOME_TYPE_OPTIONS.map((option) => (
                        <option key={option.value} value={option.value}>
                          {option.label}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div className="space-y-2">
                    <label className="block text-sm font-semibold text-slate-700" htmlFor="payer-name">
                      지급처
                    </label>
                    <input
                      className="h-12 w-full rounded-lg border-slate-200 bg-white px-4 text-sm focus:border-primary focus:ring-primary"
                      id="payer-name"
                      onChange={(event) => setForm((current) => ({ ...current, payerName: event.target.value }))}
                      type="text"
                      value={form.payerName}
                    />
                  </div>

                  {[
                    { key: "grossAmount", label: "총 지급액" },
                    { key: "taxableAmount", label: "과세 대상 금액" },
                    { key: "withheldTaxAmount", label: "원천징수세액" },
                    { key: "nonTaxableAmount", label: "비과세 금액" }
                  ].map((field) => (
                    <div key={field.key} className="space-y-2">
                      <label className="block text-sm font-semibold text-slate-700" htmlFor={field.key}>
                        {field.label}
                      </label>
                      <div className="relative">
                        <input
                          className="h-12 w-full rounded-lg border-slate-200 bg-white px-4 pr-10 text-right font-mono font-bold focus:border-primary focus:ring-primary"
                          id={field.key}
                          min="0"
                          onChange={(event) => setForm((current) => ({ ...current, [field.key]: event.target.value }))}
                          step="1"
                          type="number"
                          value={form[field.key]}
                        />
                        <span className="absolute right-3 top-1/2 -translate-y-1/2 text-sm text-slate-400">원</span>
                      </div>
                    </div>
                  ))}

                  <div className="flex justify-end gap-3 pt-4">
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

            <div className="mt-12 flex items-center justify-between border-t border-slate-200 py-12">
              <Link className="flex items-center gap-2 px-4 py-2 text-slate-600 transition-colors hover:text-primary" href="/">
                <span className="material-symbols-outlined">arrow_back</span>
                <span>이전: 대시보드</span>
              </Link>
              <Link className="flex items-center gap-2 rounded-xl bg-primary px-6 py-3 font-bold text-white shadow-lg shadow-primary/20 transition-transform hover:scale-[1.02]" href="/deductions">
                <span>다음: 공제 항목 입력</span>
                <span className="material-symbols-outlined">arrow_forward</span>
              </Link>
            </div>
          </div>
        </main>
      </div>
    </div>
  );
}
