"use client";

import Link from "next/link";
import { useEffect, useState, startTransition } from "react";
import { useRouter } from "next/navigation";

import {
  clearAuth,
  getAccessToken,
  initializeAuthenticatedContext,
  isProfileSectionComplete,
  listDeductionItems,
  listDocumentChecklists,
  listIncomeItems,
  resolveNextStep,
  resolveProgress
} from "@/lib/yearEndApi";
import {
  calculateFinancialSummary,
  formatCurrency,
  getSessionStatusText,
  getStatusBadgeClass
} from "@/lib/yearEndView";

const STAGES = [
  {
    index: 1,
    title: "기본정보 입력 및 부양가족 설정",
    description: "기본정보를 저장하고 부양가족 화면에서 1단계를 확정합니다.",
    href: "/basic-info"
  },
  {
    index: 2,
    title: "소득 확인",
    description: "급여 및 기타 소득 내역을 검토하고 다음 단계로 넘어갑니다.",
    href: "/income"
  },
  {
    index: 3,
    title: "간소화 자료 불러오기",
    description: "가져온 자료와 체크리스트를 바탕으로 증빙 상태를 점검합니다.",
    href: "/import-data"
  },
  {
    index: 4,
    title: "공제 항목 입력",
    description: "적용 가능한 공제 항목을 확인하고 금액을 반영합니다.",
    href: "/deductions"
  },
  {
    index: 5,
    title: "증빙 서류 관리",
    description: "추가 증빙과 검토 상태를 점검해 제출 준비를 마칩니다.",
    href: "/evidence-docs"
  },
  {
    index: 6,
    title: "결과 확인 및 제출",
    description: "최종 환급 예상액을 확인하고 제출 상태까지 마무리합니다.",
    href: "/results"
  }
];

function getDisplayName(user) {
  return `${user?.nickname || user?.name || "사용자"}님`;
}

function getAvatarSeed(user) {
  return (user?.nickname || user?.name || "Y").slice(0, 1).toUpperCase();
}

function resolveStageProgress(session, incomeItems, deductionItems, documentChecklists) {
  const status = session?.sessionStatus;
  const profileComplete = isProfileSectionComplete(session);
  const incomeComplete = incomeItems.length > 0 || ["CALCULATED", "SUBMITTED", "REVIEWED", "REJECTED"].includes(status);
  const importComplete = documentChecklists.length > 0 || ["CALCULATED", "SUBMITTED", "REVIEWED", "REJECTED"].includes(status);
  const deductionComplete = deductionItems.length > 0 || ["CALCULATED", "SUBMITTED", "REVIEWED", "REJECTED"].includes(status);
  const evidenceComplete = documentChecklists.some(
    (item) => item.submittedYn || item.reviewStatus === "APPROVED" || item.reviewStatus === "REJECTED"
  ) || ["SUBMITTED", "REVIEWED", "REJECTED"].includes(status);
  const resultComplete = status === "REVIEWED";

  const completionMap = {
    1: profileComplete,
    2: incomeComplete,
    3: importComplete,
    4: deductionComplete,
    5: evidenceComplete,
    6: resultComplete
  };

  let activeStage = 1;

  if (["CALCULATED", "SUBMITTED", "REVIEWED", "REJECTED"].includes(status)) {
    activeStage = 6;
  } else if (!profileComplete) {
    activeStage = 1;
  } else if (!incomeComplete) {
    activeStage = 2;
  } else if (!importComplete) {
    activeStage = 3;
  } else if (!deductionComplete) {
    activeStage = 4;
  } else if (!evidenceComplete) {
    activeStage = 5;
  } else {
    activeStage = 6;
  }

  return { activeStage, completionMap };
}

function StageCard({ stage, status }) {
  const className = status === "complete"
    ? "border-slate-200 bg-white shadow-soft"
    : status === "active"
      ? "border-primary bg-[#f6f9ff] shadow-lg"
      : "border-slate-100 bg-slate-50/80";

  const numberClass = status === "complete"
    ? "bg-blue-50 text-primary"
    : status === "active"
      ? "bg-primary text-white"
      : "bg-slate-100 text-slate-400";

  return (
    <div className={`flex items-center gap-4 rounded-[22px] border px-5 py-5 ${className}`}>
      <div className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-base font-bold ${numberClass}`}>
        {stage.index}
      </div>
      <div className="min-w-0 flex-1">
        <h4 className={`text-lg font-bold ${status === "locked" ? "text-slate-400" : "text-slate-900"}`}>
          {stage.title}
        </h4>
        <p className={`mt-1 text-sm ${status === "locked" ? "text-slate-400" : "text-slate-500"}`}>
          {stage.description}
        </p>
      </div>
      {status === "complete" ? (
        <span className="material-symbols-outlined shrink-0 text-[24px] text-green-500">check_circle</span>
      ) : null}
      {status === "locked" ? (
        <span className="material-symbols-outlined shrink-0 text-[22px] text-slate-300">lock</span>
      ) : null}
      {status !== "locked" ? (
        <Link
          className={`shrink-0 rounded-xl px-4 py-2 text-sm font-bold transition-colors ${
            status === "complete"
              ? "border border-primary bg-white text-primary hover:bg-blue-50"
              : "bg-primary text-white hover:bg-blue-600"
          }`}
          href={stage.href}
        >
          {status === "complete" && stage.index === 1 ? "수정하기" : "진행하기"}
        </Link>
      ) : null}
    </div>
  );
}

export default function DashboardPage() {
  const router = useRouter();
  const [isLoading, setIsLoading] = useState(true);
  const [isProfileOpen, setIsProfileOpen] = useState(false);
  const [user, setUser] = useState(null);
  const [session, setSession] = useState(null);
  const [incomeItems, setIncomeItems] = useState([]);
  const [deductionItems, setDeductionItems] = useState([]);
  const [documentChecklists, setDocumentChecklists] = useState([]);

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
        const [incomeList, deductionList, checklist] = await Promise.all([
          listIncomeItems(context.currentSession.id),
          listDeductionItems(context.currentSession.id),
          listDocumentChecklists(context.currentSession.id)
        ]);

        if (!active) {
          return;
        }

        setUser(context.user);
        setSession(context.currentSession);
        setIncomeItems(incomeList);
        setDeductionItems(deductionList);
        setDocumentChecklists(checklist);
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

  function handleLogout() {
    clearAuth();
    startTransition(() => {
      router.replace("/auth");
    });
  }

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 font-sans text-slate-500">
        대시보드를 불러오는 중입니다...
      </div>
    );
  }

  const displayName = getDisplayName(user);
  const avatarSeed = getAvatarSeed(user);
  const progress = resolveProgress(session);
  const nextStep = resolveNextStep(session);
  const financialSummary = calculateFinancialSummary(incomeItems, deductionItems);
  const sessionStatusText = getSessionStatusText(session?.sessionStatus);
  const phaseOneComplete = isProfileSectionComplete(session);
  const { activeStage, completionMap } = resolveStageProgress(session, incomeItems, deductionItems, documentChecklists);
  const profileBadgeClass = getStatusBadgeClass(session?.sessionStatus);

  return (
    <div className="min-h-screen bg-slate-50 text-slate-800 antialiased">
      {isProfileOpen ? (
        <button
          aria-label="프로필 닫기"
          className="fixed inset-0 z-40 bg-slate-900/10 backdrop-blur-[2px]"
          onClick={() => setIsProfileOpen(false)}
          type="button"
        />
      ) : null}

      <header className="sticky top-0 z-50 w-full border-b border-slate-200 bg-white/85 backdrop-blur-md">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-2.5 sm:px-6 lg:px-8">
          <Link className="flex items-center gap-2" href="/">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary text-white">
              <span className="material-symbols-outlined text-[20px]">calculate</span>
            </div>
            <span className="text-lg font-bold tracking-tight text-slate-900">Easy-Tax</span>
          </Link>

          <div className="flex items-center gap-4">
            <button className="relative rounded-full p-1.5 text-slate-500 transition-colors hover:bg-slate-100" type="button">
              <span className="material-symbols-outlined text-[20px]">notifications</span>
              <span className="absolute right-2 top-2 block h-2.5 w-2.5 rounded-full bg-red-500 ring-2 ring-white" />
            </button>

            <div className="relative">
              <button
                className="flex items-center gap-3 border-l border-slate-200 pl-4 transition-opacity hover:opacity-80"
                onClick={() => setIsProfileOpen((current) => !current)}
                type="button"
              >
                <div className="hidden text-right sm:block">
                  <p className="text-sm font-semibold text-slate-900">{displayName}</p>
                  <p className="text-xs text-slate-400">{user?.email || "-"}</p>
                </div>
                <div className="flex h-9 w-9 items-center justify-center overflow-hidden rounded-full bg-slate-200 text-sm font-bold text-slate-600 ring-2 ring-primary/20">
                  {avatarSeed}
                </div>
              </button>

              {isProfileOpen ? (
                <div className="absolute left-1/2 top-full z-[60] mt-4 w-[320px] -translate-x-1/2 overflow-hidden rounded-3xl border border-gray-100 bg-white shadow-[0_10px_40px_-10px_rgba(0,0,0,0.1)]">
                  <section className="flex flex-col items-center border-b border-gray-50 px-6 pb-5 pt-7 text-center">
                    <div className="mb-3 flex h-20 w-20 items-center justify-center overflow-hidden rounded-full border-4 border-white bg-slate-100 text-2xl font-bold text-slate-600 shadow-sm">
                      {avatarSeed}
                    </div>
                    <h2 className="text-xl font-bold text-gray-800">{displayName}</h2>
                    <p className="mt-1 text-sm font-medium text-primary">연말정산 서비스 사용자</p>
                  </section>

                  <section className="space-y-3 px-6 py-5">
                    <div className="flex items-center justify-between text-sm">
                      <span className="font-medium text-gray-500">닉네임</span>
                      <span className="font-bold text-gray-700">{user?.nickname || "-"}</span>
                    </div>
                    <div className="flex items-center justify-between text-sm">
                      <span className="font-medium text-gray-500">계정 ID</span>
                      <span className="font-bold text-gray-700">{user?.email || "-"}</span>
                    </div>
                    <div className="flex items-center justify-between text-sm">
                      <span className="font-medium text-gray-500">현재 상태</span>
                      <span className={`rounded-full px-2 py-1 text-xs font-bold ${profileBadgeClass}`}>
                        {sessionStatusText}
                      </span>
                    </div>
                  </section>

                  <section className="bg-[#f0f7ff] px-6 py-6">
                    <h3 className="mb-4 text-xs font-semibold uppercase tracking-wider text-gray-400">진행 요약</h3>
                    <div className="mb-6">
                      <div className="mb-2 flex items-end justify-between">
                        <span className="text-sm text-gray-600">현재 진행률</span>
                        <span className="text-base font-bold text-primary">{progress}%</span>
                      </div>
                      <div className="h-2.5 w-full rounded-full bg-gray-200">
                        <div className="h-2.5 rounded-full bg-primary transition-[width]" style={{ width: `${progress}%` }} />
                      </div>
                    </div>
                    <div className="flex items-center justify-between rounded-xl border border-blue-50 bg-white p-4 shadow-sm">
                      <span className="text-sm text-gray-500">예상 환급액</span>
                      <span className="text-lg font-bold text-blue-600">
                        {formatCurrency(financialSummary.estimatedRefund)}
                      </span>
                    </div>
                  </section>

                  <div className="grid grid-cols-2 gap-3 p-5">
                    <Link
                      className="flex items-center justify-center gap-2 rounded-2xl bg-slate-100 px-4 py-3 text-sm font-bold text-slate-700 transition-colors hover:bg-slate-200"
                      href="/basic-info"
                    >
                      <span className="material-symbols-outlined text-sm">edit</span>
                      1단계 이동
                    </Link>
                    <button
                      className="flex items-center justify-center gap-2 rounded-2xl bg-red-50 px-4 py-3 text-sm font-bold text-red-500 transition-colors hover:bg-red-100"
                      onClick={handleLogout}
                      type="button"
                    >
                      <span className="material-symbols-outlined text-sm">logout</span>
                      로그아웃
                    </button>
                  </div>
                </div>
              ) : null}
            </div>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-6 sm:px-6 lg:px-8">
        <section className="mb-6 flex flex-col items-start justify-between gap-4 md:flex-row md:items-end">
          <div>
            <h1 className="text-2xl font-bold text-slate-900 sm:text-3xl">{displayName}, 안녕하세요</h1>
            <p className="mt-1 text-slate-500">
              {session?.taxYear}년 귀속 연말정산 세션이 준비되어 있습니다.
            </p>
          </div>

          <div className="w-full rounded-2xl border border-slate-200 bg-white p-3.5 shadow-sm md:max-w-[18rem]">
            <div className="mb-2 flex items-center justify-between text-xs font-medium">
              <span className="text-slate-500">정산 진행률</span>
              <span className="text-primary">{progress}%</span>
            </div>
            <div className="h-1.5 w-full overflow-hidden rounded-full bg-slate-100">
              <div className="h-full bg-primary transition-[width]" style={{ width: `${progress}%` }} />
            </div>
            <p className="mt-2 text-xs text-slate-400">현재 세션 상태: {sessionStatusText}</p>
          </div>
        </section>

        <section className="relative mb-8 overflow-hidden rounded-3xl bg-primary p-6 text-white shadow-lg shadow-blue-200">
          <div className="relative z-10 flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
            <div className="max-w-xl">
              <p className="text-sm font-medium text-blue-100 opacity-90">
                {session?.taxYear}년 기준 예상 환급액
              </p>
              <div className="mt-2 flex items-baseline gap-2">
                <h2 className="text-4xl font-extrabold tracking-tight lg:text-[2.75rem]">
                  {new Intl.NumberFormat("ko-KR").format(financialSummary.estimatedRefund)}
                </h2>
                <span className="text-xl font-bold">원</span>
              </div>
              <p className="mt-4 text-sm leading-relaxed text-blue-100/80">
                1단계에서는 기본정보 입력과 부양가족 설정을 한 번에 마무리합니다.
                확정 버튼을 누르면 단계별 화면으로 순서대로 이어서 진행할 수 있습니다.
              </p>
              <div className="mt-6 flex gap-3">
                <Link className="rounded-xl bg-white px-5 py-2.5 text-sm font-bold text-primary shadow-sm transition-all hover:bg-blue-50" href={nextStep.href}>
                  {nextStep.label}
                </Link>
                <Link className="rounded-xl border border-white/20 bg-white/10 px-5 py-2.5 text-sm font-bold text-white transition-all hover:bg-white/20" href="/submit-status">
                  제출 상태 보기
                </Link>
              </div>
            </div>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:w-[22rem]">
              <div className="rounded-2xl border border-white/10 bg-white/10 p-4 backdrop-blur-sm">
                <p className="text-xs font-medium text-blue-200">1단계 상태</p>
                <div className="mt-1 flex items-baseline gap-1">
                  <span className="text-lg font-bold">{phaseOneComplete ? "완료" : "진행 중"}</span>
                </div>
              </div>
              <div className="rounded-2xl border border-white/10 bg-white/10 p-4 backdrop-blur-sm">
                <p className="text-xs font-medium text-blue-200">다음 화면</p>
                <div className="mt-1 flex items-baseline gap-1">
                  <span className="text-lg font-bold">{nextStep.label}</span>
                </div>
              </div>
              <div className="col-span-1 rounded-2xl border border-white/10 bg-white/5 p-3.5 sm:col-span-2">
                <div className="flex items-center justify-between text-xs">
                  <span className="text-blue-200">소득 합계</span>
                  <span className="font-semibold">{formatCurrency(financialSummary.totalGrossIncome)}</span>
                </div>
                <div className="mt-2 flex items-center justify-between text-xs">
                  <span className="text-blue-200">공제 합계</span>
                  <span className="font-semibold">{formatCurrency(financialSummary.totalDeduction)}</span>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section className="mb-10 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
          <Link className="group flex flex-col rounded-2xl border border-slate-100 bg-white p-5 shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-md" href="/dependents">
            <div className="mb-4 flex h-11 w-11 items-center justify-center rounded-xl bg-orange-50 text-orange-500 shadow-sm">
              <span className="material-symbols-outlined">group</span>
            </div>
            <h3 className="text-lg font-bold text-slate-900">부양가족 설정</h3>
            <p className="mt-1 text-sm text-slate-600">
              기본정보 다음으로 이어지는 대표 작업입니다. 등록과 확정 상태를 이곳에서 바로 확인할 수 있습니다.
            </p>
            <div className="mt-auto pt-5">
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium text-slate-500">현재 상태</span>
                <span className="text-lg font-bold text-slate-900">{phaseOneComplete ? "완료" : "진행 중"}</span>
              </div>
              <span className="mt-3 inline-flex items-center rounded-full bg-orange-50 px-3 py-1 text-xs font-semibold text-orange-600">
                {phaseOneComplete ? "확정 완료" : "확정 필요"}
              </span>
            </div>
          </Link>

          <Link className="group flex flex-col rounded-2xl border border-slate-100 bg-white p-5 shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-md" href="/income">
            <div className="mb-4 flex h-11 w-11 items-center justify-center rounded-xl bg-blue-50 text-primary">
              <span className="material-symbols-outlined">payments</span>
            </div>
            <h3 className="text-lg font-bold text-slate-900">소득 확인</h3>
            <p className="mt-1 text-sm text-slate-500">급여와 기타 소득 내역을 실제 데이터 기준으로 수정하고 저장할 수 있습니다.</p>
            <div className="mt-auto pt-5">
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium text-slate-400">등록 건수</span>
                <span className="text-lg font-bold text-slate-900">{incomeItems.length}건</span>
              </div>
              <span className="mt-3 inline-flex items-center rounded-full bg-blue-50 px-3 py-1 text-xs font-semibold text-primary">
                총 {formatCurrency(financialSummary.totalGrossIncome)}
              </span>
            </div>
          </Link>

          <Link className="group flex flex-col rounded-2xl border border-slate-100 bg-white p-5 shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-md" href="/deductions">
            <div className="mb-4 flex h-11 w-11 items-center justify-center rounded-xl bg-green-50 text-green-500">
              <span className="material-symbols-outlined">receipt_long</span>
            </div>
            <h3 className="text-lg font-bold text-slate-900">공제 항목</h3>
            <p className="mt-1 text-sm text-slate-500">공제 데이터와 증빙 준비 상태를 함께 보면서 금액을 바로 반영할 수 있습니다.</p>
            <div className="mt-auto pt-5">
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium text-slate-400">등록 건수</span>
                <span className="text-lg font-bold text-slate-900">{deductionItems.length}건</span>
              </div>
              <span className="mt-3 inline-flex items-center rounded-full bg-green-50 px-3 py-1 text-xs font-semibold text-green-600">
                총 {formatCurrency(financialSummary.totalDeduction)}
              </span>
            </div>
          </Link>
        </section>

        <section className="mb-12">
          <div className="mb-6 flex items-center justify-between">
            <h3 className="text-xl font-bold text-slate-900">정산 단계별 리스트</h3>
            <span className="text-sm text-slate-500">현재 과정의 {progress}%가 완료되었습니다.</span>
          </div>

          <div className="space-y-4">
            {STAGES.map((stage) => {
              const status = completionMap[stage.index]
                ? "complete"
                : stage.index === activeStage
                  ? "active"
                  : "locked";

              return <StageCard key={stage.index} stage={stage} status={status} />;
            })}
          </div>
        </section>

        <section className="mb-12 text-center">
          <Link className="inline-flex w-full max-w-md items-center justify-center rounded-2xl bg-primary px-8 py-5 text-lg font-bold text-white shadow-xl shadow-blue-300/50 transition-all hover:-translate-y-0.5 hover:bg-blue-600" href={nextStep.href}>
            <span>{nextStep.label}</span>
            <span className="material-symbols-outlined ml-2 text-[20px]">arrow_forward</span>
          </Link>
          <div className="mt-6">
            <Link className="text-sm font-medium text-slate-400 transition-colors hover:text-primary" href="/submit-status">
              제출 상태 화면 바로가기
            </Link>
          </div>
        </section>
      </main>
    </div>
  );
}
