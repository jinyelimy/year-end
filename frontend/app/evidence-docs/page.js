"use client";

import Link from "next/link";
import { useEffect, useState, startTransition } from "react";
import { useRouter } from "next/navigation";

import {
  clearAuth,
  getAccessToken,
  initializeAuthenticatedContext,
  listDocumentChecklists
} from "@/lib/yearEndApi";
import {
  formatDate,
  getDocumentTypeLabel,
  getReviewStatusLabel,
  getStatusBadgeClass
} from "@/lib/yearEndView";

export default function EvidenceDocsPage() {
  const router = useRouter();
  const [isLoading, setIsLoading] = useState(true);
  const [confirmReady, setConfirmReady] = useState(false);
  const [session, setSession] = useState(null);
  const [checklists, setChecklists] = useState([]);

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
        const checklist = await listDocumentChecklists(context.currentSession.id);

        if (!active) {
          return;
        }

        setSession(context.currentSession);
        setChecklists(checklist);
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

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background-light text-slate-500">
        증빙 서류 목록을 불러오는 중입니다...
      </div>
    );
  }

  const reviewedCount = checklists.filter((item) => item.reviewStatus === "APPROVED").length;
  const progress = checklists.length === 0 ? 0 : Math.round((reviewedCount / checklists.length) * 100);

  return (
    <div className="min-h-screen bg-background-light font-display text-slate-900">
      <div className="relative flex min-h-screen w-full flex-col overflow-x-hidden">
        <div className="flex grow flex-col">
          <div className="px-4 md:px-40 flex flex-1 justify-center py-5">
            <div className="flex max-w-[960px] flex-1 flex-col">
              <header className="flex items-center justify-between rounded-t-xl border-b border-slate-200 bg-white px-4 py-6 md:px-10">
                <div className="flex items-center gap-4">
                  <div className="rounded-lg bg-primary/10 p-2 text-primary">
                    <span className="material-symbols-outlined text-2xl">description</span>
                  </div>
                  <h2 className="text-xl font-bold tracking-tight text-slate-900">증빙 서류 관리</h2>
                </div>
                <Link className="flex items-center justify-center rounded-lg bg-slate-100 px-4 py-2 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-200" href="/import-data">
                  <span className="material-symbols-outlined mr-2 text-xl">arrow_back</span>
                  자료 화면
                </Link>
              </header>

              <div className="border-b border-slate-200 bg-white p-6 md:p-10">
                <div className="flex flex-col gap-4">
                  <div className="flex items-end justify-between">
                    <div className="flex flex-col gap-1">
                      <p className="text-lg font-semibold text-slate-900">전체 준비율</p>
                      <p className="text-sm text-slate-500">{session?.taxYear}년 세션에 필요한 서류를 점검하세요.</p>
                    </div>
                    <p className="text-2xl font-bold text-primary">{progress}%</p>
                  </div>
                  <div className="h-3 w-full overflow-hidden rounded-full bg-slate-100">
                    <div className="h-full bg-primary" style={{ width: `${progress}%` }} />
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="material-symbols-outlined text-sm text-slate-400">info</span>
                    <p className="text-sm font-medium text-slate-500">
                      {checklists.length}개 중 {reviewedCount}개 서류가 승인되었습니다.
                    </p>
                  </div>
                </div>
              </div>

              <div className="bg-white p-4 py-6 md:px-10">
                <h3 className="mb-6 text-lg font-bold text-slate-900">필수 서류</h3>
                <div className="space-y-4">
                  {checklists.length === 0 ? (
                    <div className="rounded-xl border border-dashed border-slate-300 bg-slate-50 p-6 text-sm text-slate-500">
                      현재 필요한 체크리스트가 없습니다. 공제 항목 입력 뒤 다시 확인해 주세요.
                    </div>
                  ) : (
                    checklists.map((item) => (
                      <div
                        key={item.id}
                        className={`flex flex-col gap-4 rounded-xl border p-4 md:flex-row md:items-center ${
                          item.reviewStatus === "REJECTED"
                            ? "border-red-200 bg-red-50/30"
                            : "border-slate-200 bg-slate-50/50"
                        }`}
                      >
                        <div className="flex flex-1 items-center gap-4">
                          <div className={`flex size-10 shrink-0 items-center justify-center rounded-full ${
                            item.reviewStatus === "APPROVED"
                              ? "bg-green-100 text-green-600"
                              : item.reviewStatus === "REJECTED"
                                ? "bg-red-100 text-red-600"
                                : "bg-amber-100 text-amber-600"
                          }`}>
                            <span className="material-symbols-outlined">
                              {item.reviewStatus === "APPROVED"
                                ? "check_circle"
                                : item.reviewStatus === "REJECTED"
                                  ? "error"
                                  : "schedule"}
                            </span>
                          </div>
                          <div className="flex flex-col">
                            <p className="text-base font-semibold text-slate-900">{getDocumentTypeLabel(item.documentType)}</p>
                            <p className="text-sm text-slate-500">
                              {item.requiredYn ? "필수 서류" : "선택 서류"}
                              {item.comment ? ` · ${item.comment}` : ""}
                            </p>
                            <p className="text-xs text-slate-400">
                              검토일 {item.reviewedAt ? formatDate(item.reviewedAt) : "-"}
                            </p>
                          </div>
                        </div>
                        <div className="flex items-center justify-between gap-3 md:justify-end">
                          <span className={`rounded-full px-3 py-1 text-xs font-semibold ${getStatusBadgeClass(item.reviewStatus)}`}>
                            {getReviewStatusLabel(item.reviewStatus)}
                          </span>
                          <button className="flex h-9 items-center justify-center rounded-lg border border-slate-200 bg-white px-4 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50" type="button">
                            보기
                          </button>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>

              <div className="rounded-b-xl border-t border-slate-200 bg-slate-50 p-6 md:p-10">
                <div className="flex flex-col items-center justify-between gap-6 md:flex-row">
                  <label className="flex items-center gap-3">
                    <input
                      checked={confirmReady}
                      className="h-5 w-5 rounded border-slate-300 text-primary focus:ring-primary"
                      onChange={(event) => setConfirmReady(event.target.checked)}
                      type="checkbox"
                    />
                    <span className="text-sm text-slate-600">
                      등록된 서류 상태를 모두 확인했고 다음 단계로 진행할 준비가 되었습니다.
                    </span>
                  </label>
                  <div className="flex w-full gap-4 md:w-auto">
                    <Link className="flex-1 rounded-lg bg-slate-200 px-8 py-3 text-center font-bold text-slate-700 transition-colors hover:bg-slate-300 md:flex-none" href="/deductions">
                      이전 단계
                    </Link>
                    <Link
                      className={`flex-1 rounded-lg px-8 py-3 text-center font-bold text-white shadow-lg shadow-primary/20 md:flex-none ${
                        confirmReady ? "bg-primary hover:opacity-95" : "cursor-not-allowed bg-primary/50"
                      }`}
                      href={confirmReady ? "/results" : "#"}
                    >
                      결과 확인으로 이동
                    </Link>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
