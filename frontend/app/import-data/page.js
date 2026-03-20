"use client";

import Link from "next/link";
import { useEffect, useState, startTransition } from "react";
import { useRouter } from "next/navigation";

import {
  clearAuth,
  getAccessToken,
  initializeAuthenticatedContext,
  listDeductionItems,
  listDocumentChecklists,
  listIncomeItems
} from "@/lib/yearEndApi";
import { formatCurrency, getDocumentTypeLabel, getReviewStatusLabel, getStatusBadgeClass } from "@/lib/yearEndView";

export default function ImportDataPage() {
  const router = useRouter();
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [session, setSession] = useState(null);
  const [incomeItems, setIncomeItems] = useState([]);
  const [deductionItems, setDeductionItems] = useState([]);
  const [checklists, setChecklists] = useState([]);

  async function loadSnapshot() {
    const context = await initializeAuthenticatedContext();
    const [incomeList, deductionList, checklist] = await Promise.all([
      listIncomeItems(context.currentSession.id),
      listDeductionItems(context.currentSession.id),
      listDocumentChecklists(context.currentSession.id)
    ]);

    setSession(context.currentSession);
    setIncomeItems(incomeList);
    setDeductionItems(deductionList);
    setChecklists(checklist);
  }

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
        await loadSnapshot();

        if (!active) {
          return;
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

  async function refreshSnapshot() {
    setIsRefreshing(true);

    try {
      await loadSnapshot();
    } finally {
      setIsRefreshing(false);
    }
  }

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background-light text-slate-500">
        자료 불러오기 화면을 준비하는 중입니다...
      </div>
    );
  }

  const completedCount = checklists.filter((item) => item.reviewStatus === "APPROVED" || item.submittedYn).length;
  const progress = checklists.length === 0 ? 20 : Math.round((completedCount / checklists.length) * 100);
  const importedAmount = deductionItems.reduce((sum, item) => sum + (Number(item.amount) || 0), 0);

  return (
    <div className="min-h-screen bg-background-light font-display text-slate-900 dark:text-slate-100">
      <div className="relative flex min-h-screen w-full flex-col overflow-x-hidden">
        <div className="flex grow flex-col">
          <header className="flex items-center justify-between border-b border-primary/10 bg-white px-6 py-4 lg:px-40">
            <div className="flex items-center gap-4">
              <div className="flex items-center text-primary">
                <span className="material-symbols-outlined text-3xl">account_balance</span>
              </div>
              <h2 className="text-lg font-bold tracking-tight text-slate-900">Easy-Tax</h2>
            </div>
            <div className="flex items-center gap-3">
              <button
                className="flex items-center justify-center rounded-lg bg-primary/10 px-4 py-2 text-sm font-bold text-primary transition-colors hover:bg-primary/20"
                onClick={() => void refreshSnapshot()}
                type="button"
              >
                <span className="material-symbols-outlined mr-2 text-lg">sync</span>
                {isRefreshing ? "새로고침 중..." : "상태 새로고침"}
              </button>
            </div>
          </header>

          <main className="flex flex-1 justify-center px-6 py-8 lg:px-40">
            <div className="flex max-w-[960px] flex-1 flex-col gap-6">
              <div className="rounded-xl border border-primary/10 bg-white p-6 shadow-sm">
                <div className="mb-6 flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
                  <div className="flex flex-col gap-1">
                    <h1 className="text-2xl font-bold tracking-tight">간소화 자료 불러오기</h1>
                    <p className="text-sm text-slate-500">
                      {session?.taxYear}년 세션 기준 소득, 공제, 증빙 상태를 한 번에 확인합니다.
                    </p>
                  </div>
                  <Link className="flex items-center justify-center rounded-lg bg-primary px-6 py-2.5 font-bold text-white shadow-md transition-all hover:brightness-110" href="/deductions">
                    <span className="material-symbols-outlined mr-2">arrow_forward</span>
                    공제 입력으로 이동
                  </Link>
                </div>

                <div className="flex flex-col gap-3 rounded-lg bg-background-light p-4">
                  <div className="flex items-center justify-between gap-6">
                    <p className="font-medium text-sm lg:text-base">전체 검토 진행률</p>
                    <p className="text-primary font-bold text-sm">{progress}% 완료</p>
                  </div>
                  <div className="h-2.5 w-full overflow-hidden rounded-full bg-slate-200">
                    <div className="h-full bg-primary" style={{ width: `${progress}%` }} />
                  </div>
                  <div className="flex items-center gap-2 text-xs text-slate-500 lg:text-sm">
                    <span className="material-symbols-outlined text-base text-primary">cloud_sync</span>
                    <span>현재 세션에 연결된 자료를 기반으로 준비 상태를 계산했습니다.</span>
                  </div>
                </div>

                <div className="mt-6 grid grid-cols-1 gap-4 md:grid-cols-2">
                  <div className="flex flex-col gap-2 rounded-lg border border-primary/10 bg-primary/5 p-5">
                    <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">연결 상태</p>
                    <div className="flex items-center gap-2">
                      <span className="material-symbols-outlined text-green-500">check_circle</span>
                      <p className="text-xl font-bold">세션 활성</p>
                    </div>
                    <p className="text-sm font-medium text-green-600">인증과 API 연결이 모두 정상입니다.</p>
                  </div>
                  <div className="flex flex-col gap-2 rounded-lg border border-primary/10 bg-primary/5 p-5">
                    <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">불러온 금액</p>
                    <div className="flex items-center gap-2">
                      <span className="material-symbols-outlined text-primary">payments</span>
                      <p className="text-xl font-bold">{formatCurrency(importedAmount)}</p>
                    </div>
                    <p className="text-sm font-medium text-slate-500">공제 항목 기준 누적 금액입니다.</p>
                  </div>
                </div>
              </div>

              <div className="flex flex-col gap-4">
                <div className="flex items-center justify-between px-2">
                  <h3 className="text-lg font-bold">확인할 자료</h3>
                  <span className="rounded bg-primary/10 px-2 py-1 text-xs font-medium text-primary">
                    소득 {incomeItems.length}건 / 공제 {deductionItems.length}건 / 서류 {checklists.length}건
                  </span>
                </div>

                <div className="space-y-3">
                  {checklists.length === 0 ? (
                    <div className="rounded-lg border border-dashed border-primary/40 bg-white p-6 text-sm text-slate-500">
                      아직 생성된 체크리스트가 없습니다. 공제 항목을 먼저 입력하면 필요한 증빙 목록이 채워집니다.
                    </div>
                  ) : (
                    checklists.map((item) => (
                      <div
                        key={item.id}
                        className="flex items-center justify-between rounded-lg border border-primary/10 bg-white p-4 transition-colors hover:border-primary/30"
                      >
                        <div className="flex items-center gap-4">
                          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
                            <span className="material-symbols-outlined">description</span>
                          </div>
                          <div>
                            <p className="font-bold text-sm lg:text-base">{getDocumentTypeLabel(item.documentType)}</p>
                            <p className="text-xs text-slate-500">
                              필수 여부: {item.requiredYn ? "필수" : "선택"} {item.comment ? `· ${item.comment}` : ""}
                            </p>
                          </div>
                        </div>
                        <div className="flex items-center gap-3">
                          <span className={`rounded-full px-3 py-1 text-xs font-semibold ${getStatusBadgeClass(item.reviewStatus)}`}>
                            {getReviewStatusLabel(item.reviewStatus)}
                          </span>
                          <Link className="text-sm font-semibold text-primary hover:underline" href="/evidence-docs">
                            자세히 보기
                          </Link>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>

              <div className="mt-4 flex flex-col items-center justify-center gap-4 sm:flex-row">
                <p className="text-center text-sm text-slate-500">증빙이 더 필요하면 서류 관리 화면에서 바로 확인할 수 있습니다.</p>
                <Link className="flex items-center gap-1 text-sm font-bold text-primary hover:underline" href="/evidence-docs">
                  <span className="material-symbols-outlined text-base">upload_file</span>
                  증빙 서류 화면으로 이동
                </Link>
              </div>
            </div>
          </main>
        </div>
      </div>
    </div>
  );
}
