"use client";

import Link from "next/link";
import { useEffect, useState, startTransition } from "react";
import { useRouter } from "next/navigation";

import {
  clearAuth,
  getAccessToken,
  hasDependentsConfirmed,
  initializeAuthenticatedContext,
  maskSsn,
  parseBasicInfo,
  saveCurrentSession,
  updateBasicInfo
} from "@/lib/yearEndApi";

const INITIAL_FORM = {
  fullName: "",
  ssn: "",
  address: "",
  city: "",
  state: "서울특별시",
  zip: "",
  notes: "",
  reportingType: "SALARY_WORKER"
};

function MessageBanner({ message }) {
  if (!message) {
    return null;
  }

  const toneClasses = message.type === "success"
    ? "border-emerald-200 bg-emerald-50 text-emerald-700"
    : "border-red-200 bg-red-50 text-red-700";

  return (
    <div className={`mx-6 mt-6 rounded-lg border px-4 py-3 text-sm ${toneClasses}`}>
      {message.text}
    </div>
  );
}

function PageIdentity({ user, session }) {
  const name = user?.nickname || user?.name || "사용자";
  const displayName = `${name}님`;
  const locked = hasDependentsConfirmed(session);

  return (
    <div className="rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm">
      <div className="mb-7 flex items-center gap-4">
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-primary text-lg font-bold text-white">
          {displayName.slice(0, 2).toUpperCase()}
        </div>
        <div>
          <h2 className="text-lg font-bold leading-tight text-slate-900">{displayName}</h2>
          <p className="mt-1 text-sm font-medium text-slate-500">
            {locked ? "1단계 완료" : "1단계 진행 중"}
          </p>
        </div>
      </div>

      <nav className="space-y-2">
        <Link
          className="flex items-center gap-3 rounded-xl bg-primary/10 px-4 py-3 text-primary transition-colors hover:bg-primary/10"
          href="/basic-info"
        >
          <span className="material-symbols-outlined text-[1.9rem]">person</span>
          <span className="text-sm font-semibold">기본정보</span>
        </Link>
        <Link
          className="flex items-center gap-3 rounded-xl px-4 py-3 text-slate-600 transition-colors hover:bg-slate-50"
          href="/dependents"
        >
          <span className="material-symbols-outlined text-[1.9rem]">group</span>
          <span className="text-sm font-semibold">부양정보</span>
        </Link>
      </nav>
    </div>
  );
}

export default function BasicInfoPage() {
  const router = useRouter();
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [message, setMessage] = useState(null);
  const [user, setUser] = useState(null);
  const [session, setSession] = useState(null);
  const [form, setForm] = useState(INITIAL_FORM);

  const locked = hasDependentsConfirmed(session);

  useEffect(() => {
    if (!getAccessToken()) {
      startTransition(() => {
        router.replace("/auth");
      });
      return;
    }

    let active = true;

    initializeAuthenticatedContext()
      .then((context) => {
        if (!active) {
          return;
        }

        const basicInfo = parseBasicInfo(context.currentSession);

        setUser(context.user);
        setSession(context.currentSession);
        setForm({
          fullName: basicInfo.fullName || "",
          ssn: basicInfo.ssnMasked || "",
          address: basicInfo.address || "",
          city: basicInfo.city || "",
          state: basicInfo.state || "서울특별시",
          zip: basicInfo.zip || "",
          notes: context.currentSession.memo || "",
          reportingType: basicInfo.reportingType || context.currentSession.filingType || "SALARY_WORKER"
        });
        setIsLoading(false);
      })
      .catch(() => {
        if (!active) {
          return;
        }

        clearAuth();
        startTransition(() => {
          router.replace("/auth");
        });
      });

    return () => {
      active = false;
    };
  }, [router]);

  async function saveForm(redirectNext) {
    if (!session?.id || locked) {
      return;
    }

    setIsSaving(true);
    setMessage(null);

    try {
      const previousBasicInfo = parseBasicInfo(session);
      const updatedSession = await updateBasicInfo(session.id, {
        basicInfoJsonb: JSON.stringify({
          ...previousBasicInfo,
          fullName: form.fullName.trim(),
          ssnMasked: maskSsn(form.ssn),
          address: form.address.trim(),
          city: form.city.trim(),
          state: form.state,
          zip: form.zip.trim(),
          reportingType: form.reportingType,
          dependentsConfirmed: false
        }),
        memo: form.notes.trim()
      });

      setSession(updatedSession);
      saveCurrentSession(updatedSession);
      setMessage({
        type: "success",
        text: redirectNext
          ? "기본정보를 저장했습니다. 부양정보 화면으로 이동합니다."
          : "기본정보를 저장했습니다."
      });

      if (redirectNext) {
        window.setTimeout(() => {
          startTransition(() => {
            router.push("/dependents");
          });
        }, 250);
      }
    } catch (error) {
      setMessage({
        type: "error",
        text: error.message || "기본정보 저장에 실패했습니다."
      });
    } finally {
      setIsSaving(false);
    }
  }

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background-light font-display text-slate-500">
        기본정보를 불러오는 중입니다...
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background-light font-display text-slate-900">
      <header className="sticky top-0 z-50 border-b border-slate-200 bg-white/90 backdrop-blur-md">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-4 sm:px-6 lg:px-8">
          <Link className="flex items-center gap-3 text-primary" href="/">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
              <span className="material-symbols-outlined">account_balance</span>
            </div>
            <span className="text-xl font-bold tracking-tight text-slate-900">Easy-Tax</span>
          </Link>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 gap-8 lg:grid-cols-12">
          <aside className="lg:col-span-4">
            <div className="sticky top-24 space-y-6">
              <PageIdentity session={session} user={user} />

              <div className="rounded-2xl border border-primary/20 bg-primary/5 p-5">
                <h3 className="text-sm font-bold text-primary">안내</h3>
                <p className="mt-2 text-sm leading-6 text-slate-600">
                  기본정보를 저장하면 다음 단계인 부양정보 입력 화면으로 이동합니다.
                  이미 1단계를 확정한 상태라면 먼저 확정을 풀어야 수정할 수 있습니다.
                </p>
              </div>
            </div>
          </aside>

          <section className="lg:col-span-8">
            <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
              <div className="border-b border-slate-200 px-6 py-6">
                <h1 className="text-2xl font-bold text-slate-900">기본정보 입력</h1>
                <p className="mt-2 text-sm text-slate-500">
                  연말정산 1단계의 시작 화면입니다. 먼저 기본정보를 확인하고 다음 단계로 이동하세요.
                </p>
              </div>

              <MessageBanner message={message} />

              <form
                className="space-y-8 px-6 py-6"
                onSubmit={(event) => {
                  event.preventDefault();
                  void saveForm(true);
                }}
              >
                <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
                  <div className="space-y-2">
                    <label className="block text-sm font-medium text-slate-700" htmlFor="full_name">
                      이름
                    </label>
                    <input
                      className="block w-full rounded-lg border-slate-300 focus:border-primary focus:ring-primary sm:text-sm"
                      disabled={locked}
                      id="full_name"
                      onChange={(event) => setForm((current) => ({ ...current, fullName: event.target.value }))}
                      type="text"
                      value={form.fullName}
                    />
                  </div>
                  <div className="space-y-2">
                    <label className="block text-sm font-medium text-slate-700" htmlFor="ssn">
                      주민등록번호(SSN)
                    </label>
                    <input
                      className="block w-full rounded-lg border-slate-300 focus:border-primary focus:ring-primary sm:text-sm"
                      disabled={locked}
                      id="ssn"
                      onChange={(event) => setForm((current) => ({ ...current, ssn: event.target.value }))}
                      placeholder="XXX-XX-XXXX"
                      type="password"
                      value={form.ssn}
                    />
                  </div>
                  <div className="space-y-2 md:col-span-2">
                    <label className="block text-sm font-medium text-slate-700" htmlFor="address">
                      주소
                    </label>
                    <input
                      className="block w-full rounded-lg border-slate-300 focus:border-primary focus:ring-primary sm:text-sm"
                      disabled={locked}
                      id="address"
                      onChange={(event) => setForm((current) => ({ ...current, address: event.target.value }))}
                      type="text"
                      value={form.address}
                    />
                  </div>
                  <div className="space-y-2">
                    <label className="block text-sm font-medium text-slate-700" htmlFor="city">
                      도시
                    </label>
                    <input
                      className="block w-full rounded-lg border-slate-300 focus:border-primary focus:ring-primary sm:text-sm"
                      disabled={locked}
                      id="city"
                      onChange={(event) => setForm((current) => ({ ...current, city: event.target.value }))}
                      type="text"
                      value={form.city}
                    />
                  </div>
                  <div className="grid grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <label className="block text-sm font-medium text-slate-700" htmlFor="state">
                        주 / 지역
                      </label>
                      <select
                        className="block w-full rounded-lg border-slate-300 focus:border-primary focus:ring-primary sm:text-sm"
                        disabled={locked}
                        id="state"
                        onChange={(event) => setForm((current) => ({ ...current, state: event.target.value }))}
                        value={form.state}
                      >
                        <option value="서울특별시">서울특별시</option>
                        <option value="경기도">경기도</option>
                        <option value="California">California</option>
                        <option value="New York">New York</option>
                      </select>
                    </div>
                    <div className="space-y-2">
                      <label className="block text-sm font-medium text-slate-700" htmlFor="zip">
                        우편번호
                      </label>
                      <input
                        className="block w-full rounded-lg border-slate-300 focus:border-primary focus:ring-primary sm:text-sm"
                        disabled={locked}
                        id="zip"
                        onChange={(event) => setForm((current) => ({ ...current, zip: event.target.value }))}
                        type="text"
                        value={form.zip}
                      />
                    </div>
                  </div>
                </div>

                <hr className="border-slate-200" />

                <div className="space-y-4">
                  <h2 className="text-lg font-semibold text-slate-900">신고 유형</h2>
                  <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                    {[
                      {
                        value: "SALARY_WORKER",
                        title: "근로소득 중심",
                        description: "급여를 기준으로 기본 연말정산 흐름을 진행합니다."
                      },
                      {
                        value: "YEAR_END_ADJUSTMENT",
                        title: "연말정산 조정",
                        description: "간소화 자료와 연결해서 정산값을 확인합니다."
                      }
                    ].map((option) => (
                      <label
                        key={option.value}
                        className="relative flex cursor-pointer rounded-xl border border-slate-200 bg-white p-4 shadow-sm transition-all has-[:checked]:border-primary has-[:checked]:ring-2 has-[:checked]:ring-primary"
                      >
                        <input
                          checked={form.reportingType === option.value}
                          className="sr-only"
                          disabled={locked}
                          name="reporting_type"
                          onChange={() => setForm((current) => ({ ...current, reportingType: option.value }))}
                          type="radio"
                          value={option.value}
                        />
                        <div>
                          <span className="block text-sm font-bold text-slate-900">{option.title}</span>
                          <span className="mt-1 block text-xs text-slate-500">{option.description}</span>
                        </div>
                      </label>
                    ))}
                  </div>
                </div>

                <hr className="border-slate-200" />

                <div className="space-y-2">
                  <label className="block text-sm font-medium text-slate-700" htmlFor="notes">
                    메모
                  </label>
                  <textarea
                    className="block w-full rounded-lg border-slate-300 focus:border-primary focus:ring-primary sm:text-sm"
                    disabled={locked}
                    id="notes"
                    onChange={(event) => setForm((current) => ({ ...current, notes: event.target.value }))}
                    rows="4"
                    value={form.notes}
                  />
                </div>

                <div className="flex items-center justify-between border-t border-slate-200 pt-6">
                  <button
                    className="rounded-xl border border-slate-200 bg-white px-6 py-2.5 text-sm font-semibold text-slate-600 transition-all hover:border-slate-300 hover:bg-slate-50 hover:text-slate-900"
                    onClick={() => startTransition(() => router.push("/"))}
                    type="button"
                  >
                    대시보드
                  </button>
                  <div className="flex gap-4">
                    {!locked ? (
                      <>
                        <button
                          className="rounded-xl border border-slate-200 bg-white px-6 py-2.5 text-sm font-semibold text-slate-700 transition-all hover:border-slate-300 hover:bg-slate-50"
                          disabled={isSaving}
                          onClick={() => void saveForm(false)}
                          type="button"
                        >
                          저장
                        </button>
                        <button
                          className="rounded-xl bg-primary px-8 py-2.5 text-sm font-semibold text-white shadow-md shadow-primary/20 transition-all hover:bg-primary/90 disabled:opacity-60"
                          disabled={isSaving}
                          type="submit"
                        >
                          {isSaving ? "저장 중..." : "다음 단계"}
                        </button>
                      </>
                    ) : null}
                  </div>
                </div>
              </form>
            </div>
          </section>
        </div>
      </main>
    </div>
  );
}
