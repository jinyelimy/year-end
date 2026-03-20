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
          1단계가 확정된 뒤에만 진입할 수 있습니다. 소득 내역을 저장하고 `2단계 확정`을 눌러야 다음 단계가 열립니다.
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
  const [selectedItemId, setSelectedItemId] = useState(null);
  const [form, setForm] = useState(INITIAL_FORM);

  const isConfirmed = hasIncomeConfirmed(session);

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
        if (items.length > 0) {
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

    const targetId = nextSelectedId || items[0]?.id;
    if (!targetId) {
      resetForm();
      return;
    }

    const selected = items.find((item) => item.id === targetId);
    if (selected) {
      selectItem(selected);
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

  async function handleSubmit(event) {
    event.preventDefault();
    if (!session?.id || isConfirmed) {
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

      await syncIncomeConfirmation(false);
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
    if (!session?.id || !selectedItemId || isConfirmed) {
      return;
    }

    setIsSaving(true);
    setMessage(null);

    try {
      await deleteIncomeItem(session.id, selectedItemId);
      await syncIncomeConfirmation(false);
      setMessage({ type: "success", text: "소득 항목을 삭제했습니다." });
      await reloadItems(null);
    } catch (error) {
      setMessage({ type: "error", text: error.message || "소득 항목 삭제에 실패했습니다." });
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

  const summary = calculateFinancialSummary(incomeItems, []);

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
              <div className="mb-8 flex items-start justify-between gap-4">
                <div>
                  <h2 className="text-2xl font-bold text-slate-900">소득 명세 확인</h2>
                  <p className="mt-2 text-sm text-slate-500">
                    2단계 확정 전에는 소득 항목을 추가/수정할 수 있고, 확정 후에는 읽기 전용으로 잠깁니다.
                  </p>
                </div>
                <button
                  className={`rounded-xl border px-4 py-2.5 text-sm font-semibold transition-all ${
                    selectedItemId && !isConfirmed
                      ? "border-red-200 bg-red-50 text-red-500 hover:border-red-300 hover:bg-red-100"
                      : "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400"
                  }`}
                  disabled={!selectedItemId || isConfirmed || isSaving}
                  onClick={handleDelete}
                  type="button"
                >
                  <span className="material-symbols-outlined text-lg">delete</span>
                  삭제
                </button>
              </div>

              {isConfirmed ? (
                <div className="mb-6 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-800">
                  2단계가 확정되어 입력이 잠겨 있습니다. 수정이 필요하면 아래의 `2단계 확정 풀기`를 눌러 주세요.
                </div>
              ) : null}

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
                  <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">기납부세액</p>
                  <p className="mt-3 text-2xl font-black text-slate-900">{formatCurrency(summary.totalWithheldTax)}</p>
                </div>
              </div>

              <MessageBanner message={message} />

              <div className="grid grid-cols-1 gap-8 xl:grid-cols-[1.35fr_0.9fr]">
                <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
                  <div className="flex items-center justify-between border-b border-slate-200 p-6">
                    <h3 className="text-lg font-bold">등록된 소득 내역</h3>
                    <span className="rounded bg-slate-100 px-2 py-1 text-xs font-medium tracking-wide text-slate-500">총 {incomeItems.length}건</span>
                  </div>
                  <div className="overflow-x-auto">
                    <table className="w-full border-collapse text-left">
                      <thead>
                        <tr className="bg-slate-50/50 text-xs font-semibold uppercase text-slate-500">
                          <th className="px-6 py-4">구분</th>
                          <th className="px-6 py-4">지급처</th>
                          <th className="px-6 py-4 text-right">총액</th>
                          <th className="px-6 py-4 text-right">기납부세액</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100">
                        {incomeItems.length === 0 ? (
                          <tr>
                            <td className="px-6 py-8 text-sm text-slate-500" colSpan="4">아직 등록된 소득 항목이 없습니다.</td>
                          </tr>
                        ) : (
                          incomeItems.map((item) => (
                            <tr key={item.id} className={`cursor-pointer transition-colors hover:bg-slate-50/80 ${item.id === selectedItemId ? "bg-primary/5" : ""}`} onClick={() => selectItem(item)}>
                              <td className="px-6 py-5 font-semibold text-slate-900">{getIncomeTypeLabel(item.incomeType)}</td>
                              <td className="px-6 py-5 text-sm text-slate-500">{item.payerName || "-"}</td>
                              <td className="px-6 py-5 text-right font-mono text-lg font-bold text-slate-900">{new Intl.NumberFormat("ko-KR").format(item.grossAmount || 0)}</td>
                              <td className="px-6 py-5 text-right font-semibold text-primary">{formatCurrency(item.withheldTaxAmount)}</td>
                            </tr>
                          ))
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
                      <label className="block text-sm font-semibold text-slate-700" htmlFor="income-type">소득 구분</label>
                      <select className={`h-12 w-full rounded-lg border px-4 text-sm ${isConfirmed ? "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400" : "border-slate-200 bg-slate-50 focus:border-primary focus:ring-primary"}`} disabled={isConfirmed} id="income-type" onChange={(event) => setForm((current) => ({ ...current, incomeType: event.target.value }))} value={form.incomeType}>
                        {INCOME_TYPE_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                      </select>
                    </div>

                    <div className="space-y-2">
                      <label className="block text-sm font-semibold text-slate-700" htmlFor="payer-name">지급처</label>
                      <input className={`h-12 w-full rounded-lg border px-4 text-sm ${isConfirmed ? "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400" : "border-slate-200 bg-white focus:border-primary focus:ring-primary"}`} disabled={isConfirmed} id="payer-name" onChange={(event) => setForm((current) => ({ ...current, payerName: event.target.value }))} type="text" value={form.payerName} />
                    </div>

                    {[
                      { key: "grossAmount", label: "총 지급액" },
                      { key: "taxableAmount", label: "과세 대상 금액" },
                      { key: "withheldTaxAmount", label: "기납부세액" },
                      { key: "nonTaxableAmount", label: "비과세 금액" }
                    ].map((field) => (
                      <div key={field.key} className="space-y-2">
                        <label className="block text-sm font-semibold text-slate-700" htmlFor={field.key}>{field.label}</label>
                        <div className="relative">
                          <input className={`h-12 w-full rounded-lg border px-4 pr-10 text-right font-mono font-bold ${isConfirmed ? "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400" : "border-slate-200 bg-white focus:border-primary focus:ring-primary"}`} disabled={isConfirmed} id={field.key} min="0" onChange={(event) => setForm((current) => ({ ...current, [field.key]: event.target.value }))} step="1" type="number" value={form[field.key]} />
                          <span className="absolute right-3 top-1/2 -translate-y-1/2 text-sm text-slate-400">원</span>
                        </div>
                      </div>
                    ))}

                    <div className="flex justify-end gap-3 pt-4">
                      {!isConfirmed ? (
                        <>
                          <button className="rounded-lg border border-slate-200 px-5 py-2.5 text-sm font-medium transition-colors hover:bg-slate-50" onClick={resetForm} type="button">초기화</button>
                          <button className="rounded-lg bg-primary px-6 py-2.5 text-sm font-semibold text-white shadow-md transition-all hover:bg-primary/90 disabled:opacity-60" disabled={isSaving} type="submit">
                            {isSaving ? "저장 중..." : selectedItemId ? "수정 저장" : "항목 추가"}
                          </button>
                        </>
                      ) : null}
                    </div>
                  </form>
                </div>
              </div>

              <div className="mt-8 flex items-center justify-between border-t border-slate-100 pt-6">
                <button className="rounded-xl border border-slate-200 bg-white px-6 py-2.5 text-sm font-semibold text-slate-600 transition-all hover:border-slate-300 hover:bg-slate-50 hover:text-slate-900" onClick={() => startTransition(() => router.push("/"))} type="button">
                  대시보드
                </button>
                <button className={`rounded-xl px-6 py-2.5 text-sm font-semibold text-white transition-all ${isConfirmed ? "bg-red-500 hover:bg-red-600" : "bg-primary hover:bg-primary/90"}`} disabled={isSaving} onClick={handleConfirmStep} type="button">
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
