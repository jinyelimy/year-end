"use client";

import Link from "next/link";
import { useEffect, useMemo, useRef, useState, startTransition } from "react";
import { useRouter } from "next/navigation";

import StageThreeSidebar from "@/components/stage-three-sidebar";
import {
  clearAuth,
  deleteDeductionItem,
  getAccessToken,
  hasDeductionsConfirmed,
  hasIncomeConfirmed,
  importHometaxPdf,
  initializeAuthenticatedContext,
  listDeductionItems,
  listDocumentChecklists,
  updateDeductionItem
} from "@/lib/yearEndApi";
import {
  buildDeductionAttributes,
  buildSuggestedImportHints,
  getConfidenceMeta,
  getDeductionSource,
  getImportBucket,
  getImportReviewMeta,
  getImportReviewReason,
  getLatestImportSummary,
  isDeductionIncludedInCalculation,
  isImportedDeduction,
  parseDeductionAttributes
} from "@/lib/deductionImport";
import {
  formatCurrency,
  formatDate,
  getDeductionTypeLabel,
  getDocumentTypeLabel,
  getReviewStatusLabel,
  getStatusBadgeClass
} from "@/lib/yearEndView";

function MessageBanner({ message }) {
  if (!message) {
    return null;
  }

  const tone = message.type === "success"
    ? "border-emerald-200 bg-emerald-50 text-emerald-700"
    : "border-red-200 bg-red-50 text-red-700";

  return <div className={`rounded-2xl border px-4 py-3 text-sm ${tone}`}>{message.text}</div>;
}

function ImportItemCard({ item, children }) {
  const source = getDeductionSource(item);
  const confidence = getConfidenceMeta(item);
  const review = getImportReviewMeta(item);
  const reviewReason = getImportReviewReason(item);

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-base font-bold text-slate-900">{getDeductionTypeLabel(item.deductionType)}</h3>
            <span className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${source.badgeClass}`}>
              {source.label}
            </span>
            <span className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${review.badgeClass}`}>
              {review.label}
            </span>
            <span className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${confidence.badgeClass}`}>
              신뢰도 {confidence.label}
            </span>
          </div>
          <p className="mt-2 text-sm text-slate-500">
            {item.sourceName || "출처 정보 없음"}
            {item.usedAt ? ` · ${formatDate(item.usedAt)}` : ""}
          </p>
          {reviewReason ? <p className="mt-2 text-sm leading-6 text-slate-600">{reviewReason}</p> : null}
        </div>

        <div className="text-right">
          <p className="text-xl font-black text-primary">{formatCurrency(item.amount)}</p>
        </div>
      </div>

      {children ? <div className="mt-4 flex flex-wrap gap-2">{children}</div> : null}
    </div>
  );
}

function SuggestionCard({ hint }) {
  return (
    <Link
      className="flex h-full flex-col rounded-2xl border border-dashed border-slate-200 bg-white p-5 transition hover:border-primary/30 hover:bg-slate-50"
      href={hint.href}
    >
      <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-primary/10 text-primary">
        <span className="material-symbols-outlined">tips_and_updates</span>
      </div>
      <h3 className="mt-4 text-base font-bold text-slate-900">{hint.title}</h3>
      <p className="mt-2 text-sm leading-6 text-slate-500">{hint.description}</p>
      <span className="mt-auto pt-5 text-sm font-semibold text-primary">deductions 화면에서 확인하기</span>
    </Link>
  );
}

export default function ImportDataPage() {
  const router = useRouter();
  const fileInputRef = useRef(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isImporting, setIsImporting] = useState(false);
  const [isMutating, setIsMutating] = useState(false);
  const [dragActive, setDragActive] = useState(false);
  const [message, setMessage] = useState(null);
  const [importStatus, setImportStatus] = useState(null);
  const [user, setUser] = useState(null);
  const [session, setSession] = useState(null);
  const [deductionItems, setDeductionItems] = useState([]);
  const [checklists, setChecklists] = useState([]);

  const isConfirmed = hasDeductionsConfirmed(session);

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

  async function handleImport(file) {
    if (!file || !session?.id || isConfirmed) {
      return;
    }

    setIsImporting(true);
    setMessage(null);
    setImportStatus({
      label: "업로드 완료",
      description: `${file.name} 파일을 서버로 전달했습니다.`
    });

    try {
      setImportStatus({
        label: "파싱 중",
        description: `${file.name} 기준으로 1차 홈택스 스냅샷을 생성하고 있습니다.`
      });

      const result = await importHometaxPdf(session.id, file);
      await loadSnapshot();

      setImportStatus({
        label: "결과 준비됨",
        description: `${result.fileName} 기준으로 ${result.importedCount}건을 가져왔습니다.`
      });
      setMessage({
        type: "success",
        text: `홈택스 가져오기가 완료되었습니다. 자동 반영 ${result.autoAppliedCount}건, 확인 필요 ${result.needsReviewCount}건을 준비했습니다.`
      });
    } catch (error) {
      setImportStatus(null);
      setMessage({
        type: "error",
        text: error.message || "간소화 자료 가져오기에 실패했습니다."
      });
    } finally {
      setIsImporting(false);
    }
  }

  function openFilePicker() {
    if (isConfirmed) {
      return;
    }
    fileInputRef.current?.click();
  }

  async function handleApprove(item) {
    if (!session?.id || isConfirmed) {
      return;
    }

    setIsMutating(true);
    setMessage(null);

    try {
      const currentAttributes = parseDeductionAttributes(item);
      await updateDeductionItem(session.id, item.id, {
        deductionType: item.deductionType,
        dependentId: item.dependentId,
        subType: item.subType,
        amount: item.amount,
        usedAt: item.usedAt,
        sourceName: item.sourceName,
        evidenceStatus: item.evidenceStatus,
        attributesJsonb: JSON.stringify(
          buildDeductionAttributes(currentAttributes, {
            importBucket: "AUTO_APPLIED",
            reviewStatus: "APPROVED"
          })
        )
      });
      await loadSnapshot();
      setMessage({ type: "success", text: "확인 필요 항목을 자동 반영 상태로 이동했습니다." });
    } catch (error) {
      setMessage({ type: "error", text: error.message || "항목 승인에 실패했습니다." });
    } finally {
      setIsMutating(false);
    }
  }

  async function handleExclude(item) {
    if (!session?.id || isConfirmed) {
      return;
    }

    setIsMutating(true);
    setMessage(null);

    try {
      await deleteDeductionItem(session.id, item.id);
      await loadSnapshot();
      setMessage({ type: "success", text: "가져온 항목을 이번 세션에서 제외했습니다." });
    } catch (error) {
      setMessage({ type: "error", text: error.message || "항목 제외에 실패했습니다." });
    } finally {
      setIsMutating(false);
    }
  }

  const importedItems = useMemo(
    () => deductionItems.filter(isImportedDeduction),
    [deductionItems]
  );
  const autoAppliedItems = useMemo(
    () => importedItems.filter((item) => getImportBucket(item) === "AUTO_APPLIED"),
    [importedItems]
  );
  const needsReviewItems = useMemo(
    () => importedItems.filter((item) => getImportBucket(item) === "NEEDS_REVIEW"),
    [importedItems]
  );
  const manualItems = useMemo(
    () => deductionItems.filter((item) => !isImportedDeduction(item)),
    [deductionItems]
  );
  const latestImport = useMemo(
    () => getLatestImportSummary(importedItems),
    [importedItems]
  );
  const activeDeductionCount = useMemo(
    () => deductionItems.filter(isDeductionIncludedInCalculation).length,
    [deductionItems]
  );
  const hints = useMemo(
    () => buildSuggestedImportHints(deductionItems),
    [deductionItems]
  );
  const checklistReadyCount = checklists.filter(
    (item) => item.reviewStatus === "APPROVED" || item.submittedYn
  ).length;

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background-light text-slate-500">
        3단계 화면을 준비하고 있습니다...
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background-light font-display text-slate-900">
      <input
        accept=".pdf,application/pdf"
        className="hidden"
        onChange={(event) => {
          const file = event.target.files?.[0];
          if (file) {
            void handleImport(file);
          }
          event.target.value = "";
        }}
        ref={fileInputRef}
        type="file"
      />

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
              isConfirmed={isConfirmed}
              manualCount={manualItems.length}
              session={session}
              user={user}
            />
          </aside>

          <section className="space-y-6 lg:col-span-9">
            <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm md:p-8">
              <div className="mb-8 flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
                <div className="max-w-3xl">
                  <span className="inline-flex rounded-full bg-primary/10 px-3 py-1 text-xs font-semibold text-primary">
                    3단계 · 간소화 자료 가져오기
                  </span>
                  <h1 className="mt-4 text-3xl font-black tracking-tight text-slate-900">
                    홈택스 자료를 가져오고,
                    <br className="hidden md:block" />
                    공제 입력으로 자연스럽게 이어지게 만듭니다.
                  </h1>
                  <p className="mt-4 text-sm leading-7 text-slate-500">
                    이번 1차 구현에서는 웹에서 PDF를 선택하면 홈택스 가져오기 흐름과 검토 화면이 바로 이어지도록 먼저 연결했습니다.
                    실제 PDF 상세 파싱 규칙은 다음 단계에서 붙일 예정이고, 지금은 가져오기 허브와 검토 가능한 자동화 UX를 우선 구현했습니다.
                  </p>
                </div>

                <Link
                  className="inline-flex items-center justify-center gap-2 rounded-xl bg-primary px-5 py-2.5 text-sm font-semibold text-white shadow-md transition hover:bg-primary/90"
                  href="/deductions"
                >
                  공제 입력 화면으로 이동
                  <span className="material-symbols-outlined text-[18px]">arrow_forward</span>
                </Link>
              </div>

              {isConfirmed ? (
                <div className="mb-6 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-800">
                  3단계가 확정된 상태라 가져오기와 검토 액션이 잠겨 있습니다. 수정이 필요하면 deductions 화면에서 먼저 3단계 확정을 풀어 주세요.
                </div>
              ) : null}

              <MessageBanner message={message} />

              <div className="grid grid-cols-1 gap-4 lg:grid-cols-[1.2fr_0.8fr]">
                <div
                  className={`rounded-3xl border-2 border-dashed p-6 transition ${
                    dragActive ? "border-primary bg-primary/5" : "border-slate-200 bg-slate-50/60"
                  }`}
                  onDragEnter={(event) => {
                    event.preventDefault();
                    if (!isConfirmed) {
                      setDragActive(true);
                    }
                  }}
                  onDragLeave={(event) => {
                    event.preventDefault();
                    setDragActive(false);
                  }}
                  onDragOver={(event) => event.preventDefault()}
                  onDrop={(event) => {
                    event.preventDefault();
                    setDragActive(false);
                    const file = event.dataTransfer.files?.[0];
                    if (file) {
                      void handleImport(file);
                    }
                  }}
                >
                  <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-white text-primary shadow-sm">
                    <span className="material-symbols-outlined text-[28px]">upload_file</span>
                  </div>
                  <h2 className="mt-5 text-xl font-bold text-slate-900">가져오기 허브</h2>
                  <p className="mt-2 text-sm leading-6 text-slate-500">
                    홈택스 또는 손택스에서 받은 PDF를 여기로 바로 가져옵니다. 웹에서는 PDF 선택과 드래그 앤 드롭을 우선 지원하고,
                    다운로드 폴더 연결은 다음 단계 범위로 남겨둡니다.
                  </p>

                  <div className="mt-6 flex flex-wrap gap-3">
                    <button
                      className="inline-flex items-center justify-center gap-2 rounded-xl bg-primary px-5 py-3 text-sm font-semibold text-white shadow-md transition hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60"
                      disabled={isImporting || isConfirmed || isMutating}
                      onClick={openFilePicker}
                      type="button"
                    >
                      <span className="material-symbols-outlined text-[18px]">folder_open</span>
                      {isImporting ? "가져오는 중..." : "PDF 선택"}
                    </button>
                    <div className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-500">
                      <span className="material-symbols-outlined text-[18px] text-slate-400">desktop_windows</span>
                      Chrome/Edge 폴더 연결은 다음 구현 범위
                    </div>
                  </div>

                  {importStatus ? (
                    <div className="mt-6 rounded-2xl border border-sky-200 bg-sky-50 px-4 py-4">
                      <div className="flex items-center gap-3">
                        <div className="flex h-10 w-10 items-center justify-center rounded-full bg-white text-sky-600">
                          <span className="material-symbols-outlined">cloud_done</span>
                        </div>
                        <div>
                          <p className="text-sm font-semibold text-sky-900">{importStatus.label}</p>
                          <p className="text-sm text-sky-700">{importStatus.description}</p>
                        </div>
                      </div>
                    </div>
                  ) : null}
                </div>

                <div className="rounded-3xl border border-slate-200 bg-slate-50/70 p-6">
                  <h2 className="text-lg font-bold text-slate-900">현재 요약</h2>
                  <div className="mt-5 space-y-4">
                    <div className="rounded-2xl border border-white bg-white p-4">
                      <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">최신 가져오기</p>
                      <p className="mt-3 text-base font-bold text-slate-900">
                        {latestImport?.fileName || "아직 가져온 파일이 없습니다"}
                      </p>
                      <p className="mt-2 text-sm text-slate-500">
                        {latestImport?.importedAt ? `${formatDate(latestImport.importedAt)} 가져옴` : "PDF를 선택하면 가져오기 기록이 여기에 표시됩니다."}
                      </p>
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                      <div className="rounded-2xl border border-white bg-white p-4">
                        <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">자동 반영</p>
                        <p className="mt-2 text-2xl font-black text-slate-900">{autoAppliedItems.length}건</p>
                      </div>
                      <div className="rounded-2xl border border-white bg-white p-4">
                        <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">확인 필요</p>
                        <p className="mt-2 text-2xl font-black text-slate-900">{needsReviewItems.length}건</p>
                      </div>
                      <div className="rounded-2xl border border-white bg-white p-4">
                        <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">현재 반영 공제</p>
                        <p className="mt-2 text-2xl font-black text-slate-900">{activeDeductionCount}건</p>
                      </div>
                      <div className="rounded-2xl border border-white bg-white p-4">
                        <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">증빙 체크</p>
                        <p className="mt-2 text-2xl font-black text-slate-900">
                          {checklistReadyCount}/{checklists.length}
                        </p>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
              <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">자동 반영됨</p>
                <p className="mt-3 text-3xl font-black text-slate-900">{autoAppliedItems.length}건</p>
                <p className="mt-2 text-sm text-slate-500">바로 deductions 합계에 반영되는 항목입니다.</p>
              </div>
              <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">확인 필요</p>
                <p className="mt-3 text-3xl font-black text-slate-900">{needsReviewItems.length}건</p>
                <p className="mt-2 text-sm text-slate-500">대상자나 분류 확인 후 승인해야 합계에 반영됩니다.</p>
              </div>
              <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">추가로 챙길 수 있음</p>
                <p className="mt-3 text-3xl font-black text-slate-900">{hints.length}건</p>
                <p className="mt-2 text-sm text-slate-500">가져온 자료 외에 놓치기 쉬운 공제를 안내합니다.</p>
              </div>
            </div>

            <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
              <div className="space-y-4 rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
                <div className="flex items-center justify-between">
                  <div>
                    <h2 className="text-xl font-bold text-slate-900">자동 반영됨</h2>
                    <p className="mt-1 text-sm text-slate-500">신뢰도가 높아 바로 반영된 홈택스 항목입니다.</p>
                  </div>
                  <span className="rounded-full bg-emerald-100 px-3 py-1 text-xs font-semibold text-emerald-700">
                    {autoAppliedItems.length}건
                  </span>
                </div>

                {autoAppliedItems.length === 0 ? (
                  <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-4 py-8 text-sm text-slate-500">
                    자동 반영된 항목이 아직 없습니다. PDF를 가져오면 여기에 홈택스 반영 결과가 표시됩니다.
                  </div>
                ) : (
                  autoAppliedItems.map((item) => (
                    <ImportItemCard item={item} key={item.id}>
                      <Link
                        className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
                        href="/deductions"
                      >
                        deductions 화면에서 수정
                      </Link>
                    </ImportItemCard>
                  ))
                )}
              </div>

              <div className="space-y-4 rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
                <div className="flex items-center justify-between">
                  <div>
                    <h2 className="text-xl font-bold text-slate-900">확인 필요</h2>
                    <p className="mt-1 text-sm text-slate-500">자동화는 되었지만 사람 검토가 필요한 항목입니다.</p>
                  </div>
                  <span className="rounded-full bg-amber-100 px-3 py-1 text-xs font-semibold text-amber-700">
                    {needsReviewItems.length}건
                  </span>
                </div>

                {needsReviewItems.length === 0 ? (
                  <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-4 py-8 text-sm text-slate-500">
                    현재 확인이 필요한 항목은 없습니다. 이제 deductions 화면에서 최종 공제 원장을 정리하면 됩니다.
                  </div>
                ) : (
                  needsReviewItems.map((item) => (
                    <ImportItemCard item={item} key={item.id}>
                      <button
                        className="rounded-xl bg-primary px-4 py-2 text-sm font-semibold text-white transition hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60"
                        disabled={isMutating || isConfirmed}
                        onClick={() => void handleApprove(item)}
                        type="button"
                      >
                        승인하고 자동 반영
                      </button>
                      <Link
                        className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
                        href="/deductions"
                      >
                        deductions 화면에서 수정
                      </Link>
                      <button
                        className="rounded-xl border border-red-200 bg-red-50 px-4 py-2 text-sm font-semibold text-red-600 transition hover:bg-red-100 disabled:cursor-not-allowed disabled:opacity-60"
                        disabled={isMutating || isConfirmed}
                        onClick={() => void handleExclude(item)}
                        type="button"
                      >
                        이번 세션에서 제외
                      </button>
                    </ImportItemCard>
                  ))
                )}
              </div>
            </div>

            <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
              <div className="mb-5 flex items-center justify-between">
                <div>
                  <h2 className="text-xl font-bold text-slate-900">추가로 챙길 수 있는 공제</h2>
                  <p className="mt-1 text-sm text-slate-500">가져온 항목 외에 사용자가 자주 놓치는 공제를 힌트로 보여줍니다.</p>
                </div>
              </div>

              <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
                {hints.map((hint) => (
                  <SuggestionCard hint={hint} key={hint.id} />
                ))}
              </div>
            </div>

            <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
              <div className="mb-5 flex items-center justify-between">
                <div>
                  <h2 className="text-xl font-bold text-slate-900">현재 증빙 체크리스트</h2>
                  <p className="mt-1 text-sm text-slate-500">자동 반영된 공제만 기준으로 4단계 증빙 목록을 미리 준비합니다.</p>
                </div>
                <Link className="text-sm font-semibold text-primary hover:underline" href="/evidence-docs">
                  증빙 화면 미리 보기
                </Link>
              </div>

              {checklists.length === 0 ? (
                <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-4 py-8 text-sm text-slate-500">
                  아직 준비된 증빙 체크리스트가 없습니다. 자동 반영 항목을 만들거나 확인 필요 항목을 승인하면 여기에 연결됩니다.
                </div>
              ) : (
                <div className="space-y-3">
                  {checklists.map((item) => (
                    <div
                      className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-slate-50/60 px-4 py-4 md:flex-row md:items-center md:justify-between"
                      key={item.id}
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
          </section>
        </div>
      </main>
    </div>
  );
}
