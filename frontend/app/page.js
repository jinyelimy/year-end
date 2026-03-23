"use client";

import Link from "next/link";
import { useEffect, useState, startTransition } from "react";
import { useRouter } from "next/navigation";

import {
  clearAuth,
  getAccessToken,
  hasDeductionsConfirmed,
  hasDependentsConfirmed,
  hasIncomeConfirmed,
  initializeAuthenticatedContext,
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
    title: "기본정보 · 부양가족",
    description: "기본정보와 부양가족을 입력한 뒤 1단계를 확정합니다.",
    href: "/dependents"
  },
  {
    index: 2,
    title: "소득 확인",
    description: "총급여와 예외 소득을 확인하고 2단계를 확정합니다.",
    href: "/income"
  },
  {
    index: 3,
    title: "간소화자료 · 공제입력",
    description: "간소화 자료를 검토하고 공제 항목을 입력한 뒤 3단계를 확정합니다.",
    href: "/import-data"
  },
  {
    index: 4,
    title: "증빙 서류 관리",
    description: "필요한 증빙 서류와 검토 상태를 확인합니다.",
    href: "/evidence-docs"
  },
  {
    index: 5,
    title: "결과 제출",
    description: "결과를 확인하고 제출까지 한 단계에서 마무리합니다.",
    href: "/results"
  }
];

const REFUND_CELEBRATION_PIECES = [
  { left: "10%", top: "14%", delay: "0s", duration: "4.8s", color: "#fef08a", size: 10 },
  { left: "22%", top: "8%", delay: "0.5s", duration: "5.1s", color: "#bfdbfe", size: 14 },
  { left: "34%", top: "16%", delay: "0.2s", duration: "4.6s", color: "#86efac", size: 8 },
  { left: "68%", top: "12%", delay: "0.8s", duration: "5.2s", color: "#fde68a", size: 12 },
  { left: "80%", top: "18%", delay: "0.4s", duration: "4.9s", color: "#f9a8d4", size: 9 },
  { left: "90%", top: "10%", delay: "1s", duration: "5.4s", color: "#93c5fd", size: 11 }
];

function getAvatarUrl(user) {
  const seed = encodeURIComponent(user?.name || user?.email || "ligg-tax");
  return `https://api.dicebear.com/9.x/adventurer/png?seed=${seed}&backgroundColor=dbeafe,c7d2fe,fde68a`;
}

function getDisplayName(user) {
  return `${user?.name || "사용자"}님`;
}

function RefundCelebrationOverlay() {
  return (
    <>
      <div className="pointer-events-none absolute inset-0 overflow-hidden rounded-[32px]">
        {REFUND_CELEBRATION_PIECES.map((piece, index) => (
          <span
            key={`${piece.left}-${piece.top}-${index}`}
            className="absolute rounded-full opacity-80"
            style={{
              left: piece.left,
              top: piece.top,
              width: `${piece.size}px`,
              height: `${piece.size}px`,
              backgroundColor: piece.color,
              animation: `dashboard-refund-confetti ${piece.duration} ease-in-out ${piece.delay} infinite`
            }}
          />
        ))}
      </div>
      <div className="pointer-events-none absolute inset-x-12 top-0 h-32 bg-gradient-to-b from-white/20 via-emerald-100/10 to-transparent blur-2xl" />
      <style jsx>{`
        @keyframes dashboard-refund-confetti {
          0% {
            transform: translate3d(0, -10px, 0) scale(0.85) rotate(0deg);
            opacity: 0;
          }
          15% {
            opacity: 0.95;
          }
          50% {
            transform: translate3d(0, 38px, 0) scale(1) rotate(140deg);
            opacity: 0.85;
          }
          100% {
            transform: translate3d(0, 92px, 0) scale(0.9) rotate(240deg);
            opacity: 0;
          }
        }
      `}</style>
    </>
  );
}

function StageCard({ stage, state }) {
  const { complete, active, locked } = state;

  const className = complete
    ? "border-slate-200 bg-white shadow-sm"
    : active
      ? "border-primary bg-[#f0f8ff] shadow-lg"
      : "border-slate-100 bg-slate-50/80";

  const numberClass = complete
    ? "bg-blue-50 text-primary"
    : active
      ? "bg-primary text-white"
      : "bg-slate-100 text-slate-400";

  const content = (
    <div className={`flex items-center gap-4 rounded-[22px] border px-5 py-5 ${className}`}>
      <div className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-base font-bold ${numberClass}`}>
        {stage.index}
      </div>

      <div className="min-w-0 flex-1">
        <h4 className={`text-lg font-bold ${locked ? "text-slate-400" : "text-slate-900"}`}>{stage.title}</h4>
        <p className={`mt-1 text-sm ${locked ? "text-slate-400" : "text-slate-500"}`}>{stage.description}</p>
      </div>

      {complete ? (
        <span className="material-symbols-outlined shrink-0 text-[24px] text-green-500">check_circle</span>
      ) : null}
      {locked ? (
        <span className="material-symbols-outlined shrink-0 text-[22px] text-slate-300">lock</span>
      ) : null}
      {!locked ? (
        <span
          className={`shrink-0 rounded-xl px-4 py-2 text-sm font-bold ${
            complete ? "border border-primary bg-white text-primary" : "bg-primary text-white"
          }`}
        >
          {complete ? "확인하기" : "진행하기"}
        </span>
      ) : null}
    </div>
  );

  if (locked) {
    return content;
  }

  return <Link href={stage.href}>{content}</Link>;
}

function SummaryCard({ title, description, amount, tone = "blue", href, enabled }) {
  const baseClass = tone === "green"
    ? "bg-green-50 text-green-600"
    : tone === "orange"
      ? "bg-orange-50 text-orange-600"
      : "bg-blue-50 text-primary";

  const inner = (
    <div
      className={`group flex h-full flex-col rounded-2xl border border-slate-100 bg-white p-5 shadow-sm transition-all ${
        enabled ? "hover:-translate-y-0.5 hover:shadow-md" : "cursor-not-allowed opacity-50"
      }`}
    >
      <div className={`mb-4 flex h-11 w-11 items-center justify-center rounded-xl ${baseClass}`}>
        <span className="material-symbols-outlined">
          {tone === "orange" ? "group" : tone === "green" ? "receipt_long" : "payments"}
        </span>
      </div>
      <h3 className="text-lg font-bold text-slate-900">{title}</h3>
      <p className="mt-1 text-sm text-slate-500">{description}</p>
      <div className="mt-auto pt-5">
        <span className={`inline-flex rounded-full px-3 py-1 text-xs font-semibold ${enabled ? baseClass : "bg-slate-100 text-slate-500"}`}>
          {amount}
        </span>
      </div>
    </div>
  );

  if (!enabled) {
    return inner;
  }

  return <Link href={href}>{inner}</Link>;
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
      <div className="flex min-h-screen items-center justify-center bg-slate-50 text-slate-500">
        대시보드를 불러오는 중입니다...
      </div>
    );
  }

  const avatarUrl = getAvatarUrl(user);
  const displayName = getDisplayName(user);
  const progress = resolveProgress(session);
  const nextStep = resolveNextStep(session);
  const financialSummary = calculateFinancialSummary(incomeItems, deductionItems);
  const isRefundTarget = financialSummary.estimatedRefund < 0;
  const displayEstimatedRefund = Math.abs(financialSummary.estimatedRefund);
  const statusText = getSessionStatusText(session?.sessionStatus);

  const stepOneComplete = hasDependentsConfirmed(session);
  const stepTwoComplete = hasIncomeConfirmed(session);
  const stepThreeComplete = hasDeductionsConfirmed(session);
  const documentsComplete = documentChecklists.some(
    (item) => item.submittedYn || item.reviewStatus === "APPROVED"
  );
  const submittedComplete = ["SUBMITTED", "REVIEWED", "REJECTED"].includes(session?.sessionStatus);

  const stageState = {
    1: { complete: stepOneComplete, active: !stepOneComplete, locked: false },
    2: { complete: stepTwoComplete, active: stepOneComplete && !stepTwoComplete, locked: !stepOneComplete },
    3: { complete: stepThreeComplete, active: stepTwoComplete && !stepThreeComplete, locked: !stepTwoComplete },
    4: { complete: documentsComplete, active: stepThreeComplete && !documentsComplete, locked: !stepThreeComplete },
    5: { complete: submittedComplete, active: documentsComplete, locked: !documentsComplete }
  };

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
            <span className="text-lg font-bold tracking-tight text-slate-900">Ligg-Tax</span>
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
                </div>
                <img alt="프로필 아바타" className="h-10 w-10 rounded-full object-cover ring-2 ring-primary/20" src={avatarUrl} />
              </button>

              {isProfileOpen ? (
                <div className="absolute left-1/2 top-full z-[60] mt-4 w-[320px] -translate-x-1/2 overflow-hidden rounded-3xl border border-gray-100 bg-white shadow-[0_10px_40px_-10px_rgba(0,0,0,0.1)]">
                  <section className="flex flex-col items-center border-b border-gray-50 px-6 pb-5 pt-7 text-center">
                    <img alt="프로필 아바타" className="mb-3 h-20 w-20 rounded-full border-4 border-white object-cover shadow-sm" src={avatarUrl} />
                    <h2 className="text-xl font-bold text-gray-800">{displayName}</h2>
                    <p className="mt-1 text-sm font-medium text-primary">연말정산 진행 중</p>
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
                      <span className={`rounded-full px-2 py-1 text-xs font-bold ${getStatusBadgeClass(session?.sessionStatus)}`}>
                        {statusText}
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
                  </section>

                  <div className="grid grid-cols-2 gap-3 p-5">
                    <Link className="flex items-center justify-center gap-2 rounded-2xl bg-slate-100 px-4 py-3 text-sm font-bold text-slate-700 transition-colors hover:bg-slate-200" href="/basic-info">
                      <span className="material-symbols-outlined text-sm">edit</span>
                      정보 수정
                    </Link>
                    <button className="flex items-center justify-center gap-2 rounded-2xl bg-red-50 px-4 py-3 text-sm font-bold text-red-500 transition-colors hover:bg-red-100" onClick={handleLogout} type="button">
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
            <p className="mt-1 text-slate-500">{session?.taxYear}년 귀속 연말정산 세션이 준비되어 있습니다.</p>
          </div>

          <div className="w-full rounded-2xl border border-slate-200 bg-white p-3.5 shadow-sm md:max-w-[18rem]">
            <div className="mb-2 flex items-center justify-between text-xs font-medium">
              <span className="text-slate-500">정산 진행률</span>
              <span className="text-primary">{progress}%</span>
            </div>
            <div className="h-1.5 w-full overflow-hidden rounded-full bg-slate-100">
              <div className="h-full bg-primary transition-[width]" style={{ width: `${progress}%` }} />
            </div>
            <p className="mt-2 text-xs text-slate-400">현재 세션 상태: {statusText}</p>
          </div>
        </section>

        <section
          className={`relative mb-8 overflow-hidden rounded-[32px] px-6 py-6 text-white shadow-xl md:px-8 md:py-8 ${
            isRefundTarget
              ? "bg-[linear-gradient(135deg,#1570ef_0%,#22c55e_100%)] shadow-[0_20px_45px_-18px_rgba(34,197,94,0.45)]"
              : "bg-[#5b8ee6] shadow-[0_20px_45px_-18px_rgba(91,142,230,0.7)]"
          }`}
        >
          {isRefundTarget ? <RefundCelebrationOverlay /> : null}

          <div className="relative z-10 flex h-full flex-col gap-6 lg:flex-row lg:items-stretch lg:justify-between">
            <div className="flex max-w-xl flex-1 flex-col justify-between">
              <p className="text-sm font-medium text-blue-100 opacity-90">{session?.taxYear}년 기준 예상 환급금</p>
              <div className="mt-3 flex items-baseline gap-2">
                <h2 className="text-5xl font-extrabold tracking-tight lg:text-[3.2rem]">
                  {new Intl.NumberFormat("ko-KR").format(displayEstimatedRefund)}
                </h2>
                <span className="text-xl font-bold">원</span>
              </div>

              {isRefundTarget ? (
                <div className="mt-4 inline-flex w-fit items-center gap-2 rounded-full border border-white/30 bg-white/14 px-4 py-2 text-sm font-bold text-white">
                  <span className="material-symbols-outlined text-[18px]">celebration</span>
                  환급 대상입니다
                </div>
              ) : null}

              <p className="mt-5 whitespace-pre-line text-[15px] leading-relaxed text-blue-50">
                {"지금까지 입력한 소득과 공제 데이터를 기준으로 계산한 예상 결과입니다.\n다음 단계 버튼을 누르면 현재 필요한 화면으로 바로 이동합니다."}
              </p>

              <div className="mt-6 flex flex-wrap gap-3">
                <Link className="rounded-xl bg-white px-6 py-2.5 text-sm font-bold text-primary shadow-sm transition-all hover:bg-blue-50" href={nextStep.href}>
                  {nextStep.label}
                </Link>
                <Link className="rounded-xl border border-white/[0.25] bg-white/[0.12] px-6 py-2.5 text-sm font-bold text-white transition-all hover:bg-white/20" href="/submit-status">
                  제출 상태 보기
                </Link>
              </div>
            </div>

            <div className="grid grid-cols-1 gap-3 self-center sm:grid-cols-2 lg:w-[31rem]">
              <div className="rounded-2xl border border-white/[0.15] bg-white/[0.12] p-5 backdrop-blur-sm">
                <p className="text-xs font-medium text-blue-100">기납부세액</p>
                <div className="mt-2 text-[1.9rem] font-bold">{formatCurrency(financialSummary.totalWithheldTax)}</div>
              </div>
              <div className="rounded-2xl border border-white/[0.15] bg-white/[0.12] p-5 backdrop-blur-sm">
                <p className="text-xs font-medium text-blue-100">결정세액</p>
                <div className="mt-2 text-[1.9rem] font-bold">{formatCurrency(financialSummary.estimatedTax)}</div>
              </div>
              <div className="col-span-1 rounded-2xl border border-white/[0.15] bg-white/[0.1] p-5 sm:col-span-2">
                <div className="flex items-center justify-between text-sm">
                  <span className="text-blue-100">총 소득</span>
                  <span className="font-semibold">{formatCurrency(financialSummary.totalGrossIncome)}</span>
                </div>
                <div className="mt-2 flex items-center justify-between text-sm">
                  <span className="text-blue-100">총 공제</span>
                  <span className="font-semibold">{formatCurrency(financialSummary.totalDeduction)}</span>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section className="mb-10 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
          <SummaryCard
            amount={stepOneComplete ? "완료 상태" : "진행 중"}
            description="기본정보와 부양가족 입력을 마무리하고 1단계를 확정할 수 있습니다."
            enabled
            href="/dependents"
            title="기본정보 · 부양가족"
            tone="orange"
          />
          <SummaryCard
            amount={stepTwoComplete ? "완료 상태" : stepOneComplete ? "진행 중" : "잠김"}
            description="1단계 완료 후 소득 내역을 확인하고 2단계를 확정할 수 있습니다."
            enabled={stepOneComplete}
            href="/income"
            title="소득 확인"
            tone="blue"
          />
          <SummaryCard
            amount={stepThreeComplete ? "완료 상태" : stepTwoComplete ? "진행 중" : "잠김"}
            description="간소화 자료 확인과 공제 항목 입력을 묶어서 3단계로 확정합니다."
            enabled={stepTwoComplete}
            href="/import-data"
            title="간소화자료 · 공제입력"
            tone="green"
          />
        </section>

        <section className="mb-12">
          <div className="mb-6 flex items-center justify-between">
            <h3 className="text-xl font-bold text-slate-900">정산 단계별 리스트</h3>
            <span className="text-sm text-slate-500">현재 과정의 {progress}%가 완료되었습니다.</span>
          </div>

          <div className="space-y-4">
            {STAGES.map((stage) => (
              <StageCard key={stage.index} stage={stage} state={stageState[stage.index]} />
            ))}
          </div>
        </section>
      </main>
    </div>
  );
}
