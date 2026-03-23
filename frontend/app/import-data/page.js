"use client";

import Link from "next/link";
import { useEffect, useMemo, useState, startTransition } from "react";
import { useRouter } from "next/navigation";

import StageThreeSidebar from "@/components/stage-three-sidebar";
import {
  clearAuth,
  getAccessToken,
  hasDeductionsConfirmed,
  hasIncomeConfirmed,
  initializeAuthenticatedContext,
  listDeductionItems,
  listDocumentChecklists
} from "@/lib/yearEndApi";
import {
  formatCurrency,
  getDeductionTypeLabel,
  getDocumentTypeLabel,
  getReviewStatusLabel,
  getStatusBadgeClass
} from "@/lib/yearEndView";

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

export default function ImportDataPage() {
  const router = useRouter();
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [user, setUser] = useState(null);
  const [session, setSession] = useState(null);
  const [deductionItems, setDeductionItems] = useState([]);
  const [checklists, setChecklists] = useState([]);

  async function loadSnapshot() {
    const context = await initializeAuthenticatedContext();

    if (!hasIncomeConfirmed(context.currentSession)) {
      startTransition(() => {
        router.replace("/income");
      });
      return false;
    }

    const [deductionList, checklist] = await Promise.all([
      listDeductionItems(context.currentSession.id),
      listDocumentChecklists(context.currentSession.id)
    ]);

    setUser(context.user);
    setSession(context.currentSession);
    setDeductionItems(deductionList);
    setChecklists(checklist);
    return true;
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
        const loaded = await loadSnapshot();
        if (!active || !loaded) {
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

  const importedItems = useMemo(
    () => deductionItems.filter((item) => getDeductionSource(item).type === "HOMETAX"),
    [deductionItems]
  );
  const manualItems = useMemo(
    () => deductionItems.filter((item) => getDeductionSource(item).type !== "HOMETAX"),
    [deductionItems]
  );
  const checklistReadyCount = checklists.filter(
    (item) => item.reviewStatus === "APPROVED" || item.submittedYn
  ).length;

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background-light text-slate-500">
        3단계 화면을 준비하는 중입니다...
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background-light font-display text-slate-900">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-4 sm:px-6 lg:px-8">
          <Link className="flex items-center gap-3 text-primary" href="/">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
              <span className="material-symbols-outlined">cloud_sync</span>
            </div>
            <span className="text-lg font-bold tracking-tight text-slate-900">Ligg-Tax</span>
          </Link>

          <button
            className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
            onClick={() => void refreshSnapshot()}
            type="button"
          >
            {isRefreshing ? "새로고침 중..." : "자료 상태 새로고침"}
          </button>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 gap-8 lg:grid-cols-12">
          <aside className="lg:col-span-3">
            <StageThreeSidebar
              activeStep="import-data"
              importedCount={importedItems.length}
              isConfirmed={hasDeductionsConfirmed(session)}
              manualCount={manualItems.length}
              session={session}
              user={user}
            />
          </aside>

          <section className="lg:col-span-9">
            <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm md:p-8">
              <div className="mb-8 flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
                <div>
                  <h1 className="text-2xl font-bold text-slate-900">간소화 자료 확인</h1>
                  <p className="mt-2 text-sm leading-6 text-slate-500">
                    홈택스에서 불러온 자료가 있으면 `간소화자료` 배지로 표시하고, 직접 입력한 항목은 `직접입력`
                    배지로 구분해서 보여줍니다. 현재 단계에서는 자료를 확인한 뒤 공제 항목 입력 화면으로
                    이어서 이동할 수 있습니다.
                  </p>
                </div>
                <Link
                  className="inline-flex items-center justify-center gap-2 rounded-xl bg-primary px-5 py-2.5 text-sm font-semibold text-white shadow-md transition hover:bg-primary/90"
                  href="/deductions"
                >
                  공제 항목 입력으로 이동
                  <span className="material-symbols-outlined text-[18px]">arrow_forward</span>
                </Link>
              </div>

              <div className="mb-8 grid grid-cols-1 gap-4 md:grid-cols-3">
                <div className="rounded-2xl border border-slate-200 bg-slate-50/70 p-5">
                  <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">간소화자료</p>
                  <p className="mt-3 text-3xl font-black text-slate-900">{importedItems.length}건</p>
                  <p className="mt-2 text-sm text-slate-500">불러온 자료가 있으면 자동으로 출처 배지가 붙습니다.</p>
                </div>
                <div className="rounded-2xl border border-slate-200 bg-slate-50/70 p-5">
                  <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">직접 입력</p>
                  <p className="mt-3 text-3xl font-black text-slate-900">{manualItems.length}건</p>
                  <p className="mt-2 text-sm text-slate-500">직접 추가한 항목은 수동 입력 자료로 계속 구분됩니다.</p>
                </div>
                <div className="rounded-2xl border border-slate-200 bg-slate-50/70 p-5">
                  <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">증빙 체크리스트</p>
                  <p className="mt-3 text-3xl font-black text-slate-900">
                    {checklistReadyCount}/{checklists.length || 0}
                  </p>
                  <p className="mt-2 text-sm text-slate-500">3단계 완료 후 증빙 단계에서 필요한 서류를 점검합니다.</p>
                </div>
              </div>

              <div className="mb-8 rounded-2xl border border-sky-200 bg-sky-50 px-5 py-4 text-sm leading-6 text-sky-800">
                실제 홈택스 자동 연동 자료가 들어오면 여기에서 같은 화면으로 확인할 수 있도록 구조를 먼저
                맞춰두었습니다. 지금은 저장된 공제 항목의 출처를 기준으로 간소화자료와 직접 입력을 구분합니다.
              </div>

              <div className="space-y-4">
                <div className="flex items-center justify-between">
                  <h2 className="text-lg font-bold text-slate-900">현재 반영된 공제 자료</h2>
                  <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-500">
                    총 {deductionItems.length}건
                  </span>
                </div>

                {deductionItems.length === 0 ? (
                  <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-5 py-8 text-sm text-slate-500">
                    아직 공제 항목이 없습니다. 다음 화면에서 직접 입력하거나, 이후 홈택스 연동 자료가 들어오면
                    이곳에서 함께 검토할 수 있습니다.
                  </div>
                ) : (
                  <div className="space-y-3">
                    {deductionItems.map((item) => {
                      const source = getDeductionSource(item);

                      return (
                        <div
                          key={item.id}
                          className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white px-5 py-4 md:flex-row md:items-center md:justify-between"
                        >
                          <div>
                            <div className="flex flex-wrap items-center gap-2">
                              <h3 className="font-semibold text-slate-900">
                                {getDeductionTypeLabel(item.deductionType)}
                              </h3>
                              <span className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${source.badgeClass}`}>
                                {source.label}
                              </span>
                            </div>
                            <p className="mt-2 text-sm text-slate-500">
                              {item.sourceName || "출처 미입력"}
                              {item.usedAt ? ` · ${item.usedAt}` : ""}
                            </p>
                          </div>
                          <div className="text-right">
                            <p className="font-bold text-primary">{formatCurrency(item.amount)}</p>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>

              <div className="mt-10 rounded-2xl border border-slate-200 bg-slate-50/70 p-5">
                <div className="mb-4 flex items-center justify-between">
                  <h2 className="text-lg font-bold text-slate-900">예상 증빙 체크리스트</h2>
                  <Link className="text-sm font-semibold text-primary hover:underline" href="/evidence-docs">
                    증빙 화면 미리보기
                  </Link>
                </div>

                {checklists.length === 0 ? (
                  <p className="text-sm text-slate-500">
                    공제 항목을 추가하면 필요한 증빙 서류 목록이 여기에서 함께 보이기 시작합니다.
                  </p>
                ) : (
                  <div className="space-y-3">
                    {checklists.map((item) => (
                      <div
                        key={item.id}
                        className="flex flex-col gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3 md:flex-row md:items-center md:justify-between"
                      >
                        <div>
                          <p className="font-semibold text-slate-900">{getDocumentTypeLabel(item.documentType)}</p>
                          <p className="mt-1 text-sm text-slate-500">
                            {item.requiredYn ? "필수 서류" : "선택 서류"}
                            {item.comment ? ` · ${item.comment}` : ""}
                          </p>
                        </div>
                        <span className={`inline-flex rounded-full px-3 py-1 text-xs font-semibold ${getStatusBadgeClass(item.reviewStatus)}`}>
                          {getReviewStatusLabel(item.reviewStatus)}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </section>
        </div>
      </main>
    </div>
  );
}
