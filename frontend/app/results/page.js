"use client";

import Link from "next/link";
import { useEffect, useState, startTransition } from "react";
import { useRouter } from "next/navigation";

import {
  clearAuth,
  getAccessToken,
  initializeAuthenticatedContext,
  listDeductionItems,
  listIncomeItems,
  saveCurrentSession,
  submitTaxSession
} from "@/lib/yearEndApi";
import {
  calculateFinancialSummary,
  formatCurrency,
  getSessionStatusText
} from "@/lib/yearEndView";

function Slice({ color, offset, value }) {
  return (
    <circle
      cx="18"
      cy="18"
      fill="transparent"
      r="15.9"
      stroke={color}
      strokeDasharray={`${value} 100`}
      strokeDashoffset={offset}
      strokeWidth="4"
    />
  );
}

export default function ResultsPage() {
  const router = useRouter();
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [message, setMessage] = useState("");
  const [session, setSession] = useState(null);
  const [incomeItems, setIncomeItems] = useState([]);
  const [deductionItems, setDeductionItems] = useState([]);

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
        const [incomeList, deductionList] = await Promise.all([
          listIncomeItems(context.currentSession.id),
          listDeductionItems(context.currentSession.id)
        ]);

        if (!active) {
          return;
        }

        setSession(context.currentSession);
        setIncomeItems(incomeList);
        setDeductionItems(deductionList);
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

  async function handleSubmit() {
    if (!session?.id || ["SUBMITTED", "REVIEWED"].includes(session.sessionStatus)) {
      startTransition(() => {
        router.push("/submit-status");
      });
      return;
    }

    setIsSubmitting(true);
    setMessage("");

    try {
      const updatedSession = await submitTaxSession(session.id);
      setSession(updatedSession);
      saveCurrentSession(updatedSession);
      setMessage("제출이 완료되었습니다. 상태 화면으로 이동합니다.");
      window.setTimeout(() => {
        startTransition(() => {
          router.push("/submit-status");
        });
      }, 250);
    } catch (error) {
      setMessage(error.message || "제출 처리에 실패했습니다.");
    } finally {
      setIsSubmitting(false);
    }
  }

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background-light text-slate-500">
        결과를 계산하는 중입니다...
      </div>
    );
  }

  const summary = calculateFinancialSummary(incomeItems, deductionItems);
  const totalBreakdown = summary.totalDeduction || 1;
  const personShare = Math.round((summary.totalDeduction * 0.55 / totalBreakdown) * 100) || 0;
  const expenseShare = Math.round((summary.totalDeduction * 0.3 / totalBreakdown) * 100) || 0;
  const otherShare = Math.max(100 - personShare - expenseShare, 0);

  return (
    <div className="min-h-screen bg-background-light font-display text-slate-900">
      <div className="relative flex min-h-screen w-full flex-col overflow-x-hidden">
        <header className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-4">
          <div className="flex items-center gap-4">
            <div className="text-primary">
              <span className="material-symbols-outlined text-3xl">account_balance_wallet</span>
            </div>
            <h2 className="text-lg font-bold tracking-tight">연말정산 {session?.taxYear}</h2>
          </div>
          <div className="flex gap-3">
            <Link className="flex h-10 w-10 items-center justify-center rounded-lg bg-slate-100 text-slate-700" href="/submit-status">
              <span className="material-symbols-outlined">notifications</span>
            </Link>
          </div>
        </header>

        <main className="flex flex-1 justify-center px-4 py-8 md:px-10">
          <div className="flex max-w-[960px] flex-1 flex-col gap-8">
            <div className="flex flex-col gap-2">
              <h1 className="text-4xl font-black tracking-tight text-slate-900">연말정산 예상 결과</h1>
              <p className="text-base text-slate-500">
                현재 입력된 소득과 공제 데이터를 기준으로 계산한 예상 결과입니다.
              </p>
            </div>

            {message ? (
              <div className={`rounded-xl border px-4 py-3 text-sm ${message.includes("실패") ? "border-red-200 bg-red-50 text-red-700" : "border-emerald-200 bg-emerald-50 text-emerald-700"}`}>
                {message}
              </div>
            ) : null}

            <div className="flex flex-col items-center gap-8 rounded-xl border border-slate-100 bg-white p-8 shadow-sm md:flex-row">
              <div className="relative flex h-48 w-48 items-center justify-center rounded-full border-[12px] border-primary/10">
                <div className="absolute inset-0 -rotate-45 rounded-full border-[12px] border-primary border-t-transparent" />
                <div className="text-center">
                  <span className="material-symbols-outlined text-5xl text-primary">celebration</span>
                  <p className="mt-1 text-sm font-semibold text-primary">{getSessionStatusText(session?.sessionStatus)}</p>
                </div>
              </div>
              <div className="flex flex-1 flex-col gap-4 text-center md:text-left">
                <div>
                  <p className="text-lg font-medium text-slate-500">최종 예상 환급액</p>
                  <h2 className="mt-1 text-5xl font-black tracking-tight text-primary">
                    {new Intl.NumberFormat("ko-KR").format(summary.estimatedRefund)}
                    원
                  </h2>
                </div>
                <div className="rounded-lg bg-primary/5 p-4">
                  <p className="leading-relaxed text-slate-700">
                    현재 기준으로는 <span className="font-bold text-primary">{summary.estimatedRefund >= 0 ? "환급" : "추가 납부"}</span> 예상입니다.
                    입력 내용을 더 보완하면 결과가 달라질 수 있습니다.
                  </p>
                </div>
                <div className="mt-2 flex flex-wrap justify-center gap-3 md:justify-start">
                  <button
                    className="rounded-lg bg-primary px-8 py-3 font-bold text-white shadow-lg shadow-primary/20 transition-colors hover:bg-primary/90 disabled:opacity-60"
                    disabled={isSubmitting}
                    onClick={handleSubmit}
                    type="button"
                  >
                    {isSubmitting
                      ? "제출 중..."
                      : ["SUBMITTED", "REVIEWED"].includes(session?.sessionStatus)
                        ? "제출 상태 보기"
                        : "최종 제출하기"}
                  </button>
                  <Link className="rounded-lg bg-slate-100 px-6 py-3 font-bold text-slate-700" href="/deductions">
                    공제 항목으로 돌아가기
                  </Link>
                </div>
              </div>
            </div>

            <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
              <div className="rounded-xl border border-slate-100 bg-white p-6 shadow-sm">
                <h3 className="mb-6 flex items-center gap-2 text-lg font-bold">
                  <span className="material-symbols-outlined text-primary">analytics</span>
                  환급 계산 요약
                </h3>
                <div className="space-y-6">
                  <div className="flex flex-col gap-2">
                    <div className="flex justify-between text-sm">
                      <span className="text-slate-500">총 과세소득</span>
                      <span className="font-semibold">{formatCurrency(summary.totalTaxableIncome)}</span>
                    </div>
                    <div className="h-3 w-full overflow-hidden rounded-full bg-slate-100">
                      <div className="h-full w-[60%] bg-primary" />
                    </div>
                  </div>
                  <div className="flex flex-col gap-2">
                    <div className="flex justify-between text-sm">
                      <span className="text-slate-500">총 공제액</span>
                      <span className="font-semibold">{formatCurrency(summary.totalDeduction)}</span>
                    </div>
                    <div className="h-3 w-full overflow-hidden rounded-full bg-slate-100">
                      <div className="h-full w-[45%] bg-slate-400" />
                    </div>
                  </div>
                  <div className="flex items-center justify-between border-t border-slate-100 pt-4">
                    <span className="font-bold text-slate-900">원천징수세액</span>
                    <span className="text-2xl font-black text-primary">{formatCurrency(summary.totalWithheldTax)}</span>
                  </div>
                </div>
              </div>

              <div className="rounded-xl border border-slate-100 bg-white p-6 shadow-sm">
                <h3 className="mb-6 flex items-center gap-2 text-lg font-bold">
                  <span className="material-symbols-outlined text-primary">pie_chart</span>
                  공제 비중
                </h3>
                <div className="flex items-center justify-center gap-8 py-4">
                  <svg className="h-32 w-32" viewBox="0 0 36 36">
                    <circle cx="18" cy="18" fill="transparent" r="15.9" stroke="#e2e8f0" strokeWidth="4" />
                    <Slice color="#2b8cee" offset="25" value={personShare} />
                    <Slice color="#94a3b8" offset={25 - personShare} value={expenseShare} />
                    <Slice color="#cbd5e1" offset={25 - personShare - expenseShare} value={otherShare} />
                  </svg>
                  <div className="flex flex-col gap-2">
                    <div className="flex items-center gap-2 text-sm">
                      <span className="h-3 w-3 rounded-full bg-primary" />
                      <span className="text-slate-500">주요 공제</span>
                      <span className="font-bold">{personShare}%</span>
                    </div>
                    <div className="flex items-center gap-2 text-sm">
                      <span className="h-3 w-3 rounded-full bg-slate-400" />
                      <span className="text-slate-500">생활/증빙</span>
                      <span className="font-bold">{expenseShare}%</span>
                    </div>
                    <div className="flex items-center gap-2 text-sm">
                      <span className="h-3 w-3 rounded-full bg-slate-200" />
                      <span className="text-slate-500">기타</span>
                      <span className="font-bold">{otherShare}%</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
              <div className="flex flex-col gap-2 rounded-xl border border-slate-100 bg-white p-5">
                <span className="material-symbols-outlined text-primary">info</span>
                <p className="text-sm font-bold">총급여액</p>
                <p className="text-xl font-bold">{formatCurrency(summary.totalGrossIncome)}</p>
              </div>
              <div className="flex flex-col gap-2 rounded-xl border border-slate-100 bg-white p-5">
                <span className="material-symbols-outlined text-primary">local_atm</span>
                <p className="text-sm font-bold">소득공제 합계</p>
                <p className="text-xl font-bold">{formatCurrency(summary.totalDeduction)}</p>
              </div>
              <div className="flex flex-col gap-2 rounded-xl border border-slate-100 bg-white p-5">
                <span className="material-symbols-outlined text-primary">receipt_long</span>
                <p className="text-sm font-bold">세션 상태</p>
                <p className="text-xl font-bold">{getSessionStatusText(session?.sessionStatus)}</p>
              </div>
            </div>

            <div className="rounded-xl border border-primary/20 bg-primary/5 p-6">
              <div className="flex items-start gap-4">
                <div className="rounded-lg bg-primary p-2 text-white">
                  <span className="material-symbols-outlined">lightbulb</span>
                </div>
                <div>
                  <h4 className="mb-1 font-bold text-primary">정산 메모</h4>
                  <p className="text-sm text-slate-700">
                    {session?.memo || "추가 메모가 없습니다. 수정이 필요하면 기본정보나 공제 화면으로 돌아가 조정하세요."}
                  </p>
                </div>
              </div>
            </div>

            <div className="flex items-center justify-between border-t border-slate-200 pt-8">
              <Link className="flex items-center gap-2 text-slate-600 hover:text-primary" href="/deductions">
                <span className="material-symbols-outlined">arrow_back</span>
                <span>이전: 공제 입력</span>
              </Link>
              <Link className="rounded-lg bg-primary px-6 py-3 font-bold text-white hover:bg-primary/90" href="/">
                대시보드로 이동
              </Link>
            </div>
          </div>
        </main>
      </div>
    </div>
  );
}
