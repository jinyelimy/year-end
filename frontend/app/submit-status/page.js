"use client";

import Link from "next/link";
import { useEffect, useState, startTransition } from "react";
import { useRouter } from "next/navigation";

import { clearAuth, getAccessToken, initializeAuthenticatedContext } from "@/lib/yearEndApi";
import { formatDate, getSessionStatusText, getStatusBadgeClass } from "@/lib/yearEndView";

function getStatusModel(session) {
  const status = session?.sessionStatus;

  if (status === "REVIEWED") {
    return {
      title: "검토 완료",
      subtitle: "제출된 연말정산이 검토 완료 상태입니다.",
      badge: "검토 완료",
      tone: "bg-emerald-100 text-emerald-700",
      actionHref: "/",
      actionLabel: "대시보드로 이동",
      note: "검토가 완료되어 추가 조치는 필요하지 않습니다."
    };
  }

  if (status === "REJECTED") {
    return {
      title: "보완 필요",
      subtitle: "제출 서류 또는 입력값에 보완이 필요합니다.",
      badge: "보완 필요",
      tone: "bg-red-100 text-red-700",
      actionHref: "/evidence-docs",
      actionLabel: "증빙 서류 보완",
      note: "거절 사유를 확인하고 서류 또는 데이터를 다시 준비해 주세요."
    };
  }

  if (status === "SUBMITTED") {
    return {
      title: "제출 완료",
      subtitle: "제출이 완료되었고 현재 검토 대기 또는 진행 중입니다.",
      badge: "검토 대기 중",
      tone: "bg-amber-100 text-amber-700",
      actionHref: "/results",
      actionLabel: "결과 화면 보기",
      note: "검토가 끝날 때까지는 입력 데이터를 수정할 수 없습니다."
    };
  }

  if (status === "CALCULATED") {
    return {
      title: "제출 전 상태",
      subtitle: "계산은 끝났지만 아직 최종 제출은 하지 않았습니다.",
      badge: "제출 필요",
      tone: "bg-blue-100 text-blue-700",
      actionHref: "/results",
      actionLabel: "최종 제출하러 가기",
      note: "결과 화면에서 최종 제출 버튼을 눌러 제출을 완료할 수 있습니다."
    };
  }

  return {
    title: "작성 중",
    subtitle: "아직 제출 전 단계입니다. 입력을 마무리해 주세요.",
    badge: "입력 진행 중",
    tone: "bg-slate-100 text-slate-700",
    actionHref: "/basic-info",
    actionLabel: "입력 이어서 하기",
    note: "기본정보와 부양가족, 소득/공제 입력을 먼저 완료해야 제출할 수 있습니다."
  };
}

export default function SubmitStatusPage() {
  const router = useRouter();
  const [isLoading, setIsLoading] = useState(true);
  const [session, setSession] = useState(null);
  const [user, setUser] = useState(null);

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

        if (!active) {
          return;
        }

        setSession(context.currentSession);
        setUser(context.user);
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
        제출 상태를 불러오는 중입니다...
      </div>
    );
  }

  const model = getStatusModel(session);
  const referenceId = session?.id ? String(session.id).slice(0, 8).toUpperCase() : "N/A";
  const submittedAt = session?.submittedAt ? formatDate(session.submittedAt) : "아직 제출 전";

  return (
    <div className="min-h-screen bg-background-light font-display text-slate-900">
      <div className="relative flex min-h-screen w-full flex-col overflow-x-hidden">
        <div className="flex grow flex-col">
          <header className="sticky top-0 z-50 flex items-center justify-between border-b border-slate-200 bg-white px-6 py-3 md:px-10">
            <div className="flex items-center gap-4">
              <div className="flex size-8 items-center justify-center rounded-lg bg-primary text-white">
                <span className="material-symbols-outlined">verified</span>
              </div>
              <h2 className="text-lg font-bold tracking-tight">Easy-Tax</h2>
            </div>
            <div className="hidden items-center gap-3 border-l border-slate-200 pl-4 md:flex">
              <div className="flex flex-col items-end">
                <p className="text-sm font-semibold">{user?.nickname || user?.name || "사용자"}</p>
                <p className="text-xs text-slate-500">{user?.email || "-"}</p>
              </div>
              <div className="flex size-10 items-center justify-center rounded-full bg-slate-200 text-sm font-bold text-slate-600">
                {(user?.nickname || user?.name || "Y").slice(0, 1).toUpperCase()}
              </div>
            </div>
          </header>

          <main className="flex flex-1 justify-center px-4 py-8 md:px-10">
            <div className="flex w-full max-w-4xl flex-col gap-6">
              <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
                <div className="mb-8 flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
                  <div>
                    <h1 className="text-2xl font-bold text-slate-900">{model.title}</h1>
                    <p className="mt-1 text-slate-500">
                      참조 ID: #{referenceId} · 제출일: {submittedAt}
                    </p>
                  </div>
                  <div className={`self-start rounded-full px-4 py-2 text-sm font-semibold ${model.tone}`}>
                    {model.badge}
                  </div>
                </div>

                <div className="relative flex flex-col items-start justify-between gap-4 px-4 md:flex-row md:items-center md:gap-0">
                  <div className="absolute left-10 right-10 top-[18px] hidden h-0.5 bg-slate-200 md:block" />
                  {[
                    { title: "입력 완료", subtitle: "데이터 준비", icon: "check", active: true, done: true },
                    {
                      title: "최종 검토",
                      subtitle: ["SUBMITTED", "REVIEWED", "REJECTED"].includes(session?.sessionStatus) ? "진행 중 또는 완료" : "제출 전",
                      icon: ["SUBMITTED", "REVIEWED", "REJECTED"].includes(session?.sessionStatus) ? "sync" : "hourglass_empty",
                      active: ["SUBMITTED", "REVIEWED", "REJECTED"].includes(session?.sessionStatus),
                      done: session?.sessionStatus === "REVIEWED"
                    },
                    {
                      title: "최종 상태",
                      subtitle: getSessionStatusText(session?.sessionStatus),
                      icon: session?.sessionStatus === "REJECTED" ? "error" : "verified",
                      active: ["REVIEWED", "REJECTED"].includes(session?.sessionStatus),
                      done: session?.sessionStatus === "REVIEWED"
                    }
                  ].map((step, index) => (
                    <div key={step.title} className="relative z-10 flex items-center gap-4 text-center md:flex-col md:gap-2">
                      <div className={`flex size-9 items-center justify-center rounded-full ring-4 ring-white ${
                        step.done
                          ? "bg-green-500 text-white"
                          : step.active
                            ? "bg-primary text-white"
                            : "bg-slate-200 text-slate-400"
                      }`}>
                        <span className="material-symbols-outlined text-[20px]">{step.icon}</span>
                      </div>
                      <div className="text-left md:text-center">
                        <p className={`text-sm font-bold ${step.active || step.done ? "text-slate-900" : "text-slate-400"}`}>
                          {step.title}
                        </p>
                        <p className={`text-xs ${index === 1 ? "text-primary" : "text-slate-500"}`}>{step.subtitle}</p>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              <div className={`rounded-xl border p-6 ${session?.sessionStatus === "REJECTED" ? "border-red-200 bg-red-50" : "border-slate-200 bg-white"}`}>
                <div className="flex items-start gap-4">
                  <div className={`flex size-10 shrink-0 items-center justify-center rounded-lg ${
                    session?.sessionStatus === "REJECTED"
                      ? "bg-red-100 text-red-600"
                      : "bg-primary/10 text-primary"
                  }`}>
                    <span className="material-symbols-outlined">
                      {session?.sessionStatus === "REJECTED" ? "error" : "info"}
                    </span>
                  </div>
                  <div className="flex-1">
                    <h3 className={`text-lg font-bold ${session?.sessionStatus === "REJECTED" ? "text-red-800" : "text-slate-900"}`}>
                      {model.subtitle}
                    </h3>
                    <p className={`mt-1 text-sm leading-relaxed ${session?.sessionStatus === "REJECTED" ? "text-red-700" : "text-slate-600"}`}>
                      {model.note}
                    </p>
                    <div className="mt-4 flex flex-wrap gap-3">
                      <Link className="rounded-lg bg-primary px-5 py-2 text-sm font-bold text-white transition-colors hover:bg-primary/90" href={model.actionHref}>
                        {model.actionLabel}
                      </Link>
                      <Link className="rounded-lg border border-slate-200 bg-white px-5 py-2 text-sm font-bold text-slate-700 transition-colors hover:bg-slate-50" href="/results">
                        상세 결과 보기
                      </Link>
                    </div>
                  </div>
                </div>
              </div>

              <div className="grid gap-4 md:grid-cols-2">
                <div className="flex flex-col justify-between rounded-xl border border-slate-200 bg-white p-6">
                  <div>
                    <h4 className="font-bold text-slate-900">현재 세션 상태</h4>
                    <p className="mt-2 text-sm text-slate-500">연말정산 세션의 최신 진행 상태입니다.</p>
                    <span className={`mt-4 inline-flex rounded-full px-3 py-1 text-xs font-semibold ${getStatusBadgeClass(session?.sessionStatus)}`}>
                      {getSessionStatusText(session?.sessionStatus)}
                    </span>
                  </div>
                  <Link className="mt-4 flex items-center gap-1 text-sm font-bold text-primary hover:underline" href="/">
                    <span className="material-symbols-outlined text-[18px]">home</span>
                    대시보드 보기
                  </Link>
                </div>
                <div className="flex flex-col justify-between rounded-xl border border-primary/20 bg-primary/10 p-6">
                  <div>
                    <h4 className="font-bold text-primary">다음 권장 작업</h4>
                    <p className="mt-2 text-sm text-slate-600">
                      {session?.sessionStatus === "REJECTED"
                        ? "보완이 필요한 증빙과 입력값을 먼저 정리하세요."
                        : session?.sessionStatus === "SUBMITTED"
                          ? "검토 결과가 반영될 때까지 상태 화면을 확인해 주세요."
                          : "결과 화면에서 제출 여부를 최종 결정할 수 있습니다."}
                    </p>
                  </div>
                  <Link className="mt-4 flex items-center justify-center gap-2 rounded-lg bg-primary px-6 py-2.5 text-sm font-bold text-white shadow-md shadow-primary/20" href={model.actionHref}>
                    {model.actionLabel}
                    <span className="material-symbols-outlined text-[18px]">arrow_forward</span>
                  </Link>
                </div>
              </div>

              <div className="mt-2 flex flex-wrap gap-2 border-t border-slate-200 py-4">
                <Link className="flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-100" href="/">
                  <span className="material-symbols-outlined text-[20px]">home</span>
                  홈
                </Link>
                <Link className="flex items-center gap-2 rounded-lg bg-primary/10 px-4 py-2 text-sm font-bold text-primary" href="/results">
                  <span className="material-symbols-outlined text-[20px]">description</span>
                  결과 화면
                </Link>
                <Link className="flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-100" href="/evidence-docs">
                  <span className="material-symbols-outlined text-[20px]">mail</span>
                  증빙 서류
                </Link>
                <Link className="flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-100" href="/auth">
                  <span className="material-symbols-outlined text-[20px]">person</span>
                  계정 화면
                </Link>
              </div>
            </div>
          </main>

          <footer className="border-t border-slate-200 py-6 text-center text-xs text-slate-500">
            Review Status Portal · Easy-Tax
          </footer>
        </div>
      </div>
    </div>
  );
}
