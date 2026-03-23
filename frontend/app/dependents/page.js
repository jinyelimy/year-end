"use client";

import Link from "next/link";
import { useEffect, useState, startTransition } from "react";
import { useRouter } from "next/navigation";

import {
  clearAuth,
  createDependent,
  deleteDependent,
  getAccessToken,
  hasDependentsConfirmed,
  initializeAuthenticatedContext,
  listDependents,
  parseBasicInfo,
  saveCurrentSession,
  updateBasicInfo,
  updateDependent
} from "@/lib/yearEndApi";

const INITIAL_FORM = {
  name: "",
  relationType: "CHILD",
  birthDate: "",
  annualIncomeAmount: "0",
  residentType: "RESIDENT",
  livesTogether: true,
  disabled: false,
  basicDeductionTarget: true
};

const RELATION_LABELS = {
  SELF: "본인",
  SPOUSE: "배우자",
  CHILD: "자녀",
  PARENT: "부모"
};

function MessageBanner({ message }) {
  if (!message) {
    return null;
  }

  const toneClasses = message.type === "success"
    ? "border-emerald-200 bg-emerald-50 text-emerald-700"
    : "border-red-200 bg-red-50 text-red-700";

  return <div className={`mb-4 rounded-lg border px-4 py-3 text-sm ${toneClasses}`}>{message.text}</div>;
}

function Sidebar({ dependentCount, isConfirmed, onNewDependent, user }) {
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
            <h1 className="text-lg font-bold leading-tight text-slate-900">{displayName}</h1>
            <p className="mt-1 text-sm font-medium text-slate-500">{isConfirmed ? "1단계 완료" : "1단계 진행 중"}</p>
          </div>
        </div>

        <nav className="space-y-2">
          <Link className="flex items-center gap-3 rounded-xl px-4 py-3 text-slate-600 transition-colors hover:bg-slate-50" href="/basic-info">
            <span className="material-symbols-outlined text-[1.9rem]">person</span>
            <span className="text-sm font-semibold">기본정보</span>
          </Link>
          <Link className="flex items-center gap-3 rounded-xl bg-primary/10 px-4 py-3 text-primary" href="/dependents">
            <span className="material-symbols-outlined text-[1.9rem]">group</span>
            <span className="text-sm font-semibold">부양가족</span>
          </Link>
        </nav>
      </div>

      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="flex items-center justify-between border-b border-slate-100 px-4 py-4">
          <h2 className="font-bold text-slate-900">등록된 부양가족</h2>
          <div className="flex items-center gap-2">
            <span className="rounded-full bg-slate-100 px-2 py-1 text-xs font-bold text-slate-500">총 {dependentCount}명</span>
            {!isConfirmed ? (
              <button className="rounded-xl bg-primary px-4 py-2 text-xs font-bold text-white shadow-sm shadow-primary/20 transition-all hover:bg-primary/90" onClick={onNewDependent} type="button">
                새로 추가
              </button>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  );
}

function FieldWrapper({ children, disabled }) {
  return (
    <div className={`rounded-xl border p-3 transition-all ${
      disabled ? "border-slate-200 bg-slate-100/80" : "border-transparent bg-transparent p-0"
    }`}>
      {children}
    </div>
  );
}

export default function DependentsPage() {
  const router = useRouter();
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [message, setMessage] = useState(null);
  const [user, setUser] = useState(null);
  const [session, setSession] = useState(null);
  const [dependents, setDependents] = useState([]);
  const [selectedDependentId, setSelectedDependentId] = useState(null);
  const [form, setForm] = useState(INITIAL_FORM);

  const isConfirmed = hasDependentsConfirmed(session);

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
        const currentDependents = await listDependents(context.currentSession.id);
        if (!active) {
          return;
        }

        setUser(context.user);
        setSession(context.currentSession);
        setDependents(currentDependents);
        if (currentDependents.length > 0) {
          fillForm(currentDependents[0]);
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
    setSelectedDependentId(null);
    setForm(INITIAL_FORM);
  }

  function fillForm(dependent) {
    setSelectedDependentId(dependent.id);
    setForm({
      name: dependent.name || "",
      relationType: dependent.relationType || "CHILD",
      birthDate: dependent.birthDate || "",
      annualIncomeAmount: String(dependent.annualIncomeAmount ?? 0),
      residentType: dependent.residentType || "RESIDENT",
      livesTogether: Boolean(dependent.livesTogether),
      disabled: Boolean(dependent.disabled),
      basicDeductionTarget: Boolean(dependent.basicDeductionTarget)
    });
  }

  async function syncStepConfirmation(confirmed) {
    const basicInfo = parseBasicInfo(session);
    const updatedSession = await updateBasicInfo(session.id, {
      basicInfoJsonb: JSON.stringify({
        ...basicInfo,
        dependentsConfirmed: confirmed,
        incomeConfirmed: confirmed ? basicInfo.incomeConfirmed === true : false
      }),
      memo: session?.memo || ""
    });

    setSession(updatedSession);
    saveCurrentSession(updatedSession);
    return updatedSession;
  }

  async function refreshDependents(nextSelectedDependentId = selectedDependentId) {
    if (!session?.id) {
      return;
    }

    setIsRefreshing(true);

    try {
      const currentDependents = await listDependents(session.id);
      setDependents(currentDependents);

      const targetId = nextSelectedDependentId || currentDependents[0]?.id;
      if (!targetId) {
        resetForm();
        return;
      }

      const selected = currentDependents.find((item) => item.id === targetId);
      if (selected) {
        fillForm(selected);
      }
    } finally {
      setIsRefreshing(false);
    }
  }

  async function handleSave(event) {
    event.preventDefault();
    if (!session?.id || isConfirmed) {
      return;
    }

    setIsSaving(true);
    setMessage(null);

    try {
      const payload = {
        name: form.name.trim(),
        relationType: form.relationType,
        birthDate: form.birthDate,
        annualIncomeAmount: Number(form.annualIncomeAmount || 0),
        residentType: form.residentType,
        livesTogether: form.livesTogether,
        disabled: form.disabled,
        basicDeductionTarget: form.basicDeductionTarget
      };

      if (selectedDependentId) {
        await updateDependent(session.id, selectedDependentId, payload);
        setMessage({ type: "success", text: "부양가족 정보를 수정했습니다." });
      } else {
        await createDependent(session.id, payload);
        setMessage({ type: "success", text: "부양가족을 등록했습니다." });
      }

      await syncStepConfirmation(false);
      await refreshDependents(selectedDependentId);
      if (!selectedDependentId) {
        resetForm();
      }
    } catch (error) {
      setMessage({ type: "error", text: error.message || "부양가족 저장에 실패했습니다." });
    } finally {
      setIsSaving(false);
    }
  }

  async function handleDelete() {
    if (!session?.id || !selectedDependentId || isConfirmed) {
      return;
    }

    setIsSaving(true);
    setMessage(null);

    try {
      await deleteDependent(session.id, selectedDependentId);
      await syncStepConfirmation(false);
      setMessage({ type: "success", text: "부양가족을 삭제했습니다." });
      await refreshDependents(null);
    } catch (error) {
      setMessage({ type: "error", text: error.message || "부양가족 삭제에 실패했습니다." });
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
        await syncStepConfirmation(false);
        setMessage({ type: "success", text: "1단계 확정을 풀었습니다. 다시 수정할 수 있습니다." });
      } else {
        await syncStepConfirmation(true);
        setMessage({ type: "success", text: "기본정보 및 부양가족 1단계 확정을 완료했습니다." });
        window.setTimeout(() => {
          startTransition(() => {
            router.replace("/");
          });
        }, 250);
      }
    } catch (error) {
      setMessage({ type: "error", text: error.message || "1단계 확정 처리에 실패했습니다." });
    } finally {
      setIsSaving(false);
    }
  }

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background-light text-slate-500">
        부양가족 정보를 불러오는 중입니다...
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background-light font-display text-slate-900">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-4 sm:px-6 lg:px-8">
          <Link className="flex items-center gap-3 text-primary" href="/">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
              <span className="material-symbols-outlined">family_history</span>
            </div>
            <span className="text-lg font-bold tracking-tight text-slate-900">Ligg-Tax</span>
          </Link>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 gap-8 lg:grid-cols-12">
          <aside className="lg:col-span-4">
            <Sidebar dependentCount={dependents.length} isConfirmed={isConfirmed} onNewDependent={resetForm} user={user} />

            <div className="mt-6 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
              <div className="divide-y divide-slate-100">
                {isRefreshing ? (
                  <div className="p-6 text-sm text-slate-500">목록을 새로 불러오는 중입니다...</div>
                ) : dependents.length === 0 ? (
                  <div className="p-6 text-sm text-slate-500">등록된 부양가족이 없습니다. 필요하면 새 항목을 추가해 주세요.</div>
                ) : (
                  dependents.map((dependent) => {
                    const active = dependent.id === selectedDependentId;

                    return (
                      <button
                        key={dependent.id}
                        className={`w-full p-4 text-left transition-colors ${active ? "border-l-4 border-primary bg-primary/5" : "hover:bg-slate-50"}`}
                        onClick={() => fillForm(dependent)}
                        type="button"
                      >
                        <div className="flex items-center justify-between gap-3">
                          <div className="flex items-center gap-3">
                            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-100 font-bold text-blue-600">
                              {dependent.name.slice(0, 2).toUpperCase()}
                            </div>
                            <div>
                              <p className="text-sm font-semibold text-slate-900">{dependent.name}</p>
                              <p className="text-xs text-slate-500">
                                {RELATION_LABELS[dependent.relationType] || dependent.relationType} · {dependent.birthDate || "생년월일 미입력"}
                              </p>
                            </div>
                          </div>
                        </div>
                      </button>
                    );
                  })
                )}
              </div>
            </div>
          </aside>

          <section className="lg:col-span-8">
            <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm md:p-8">
              <div className="mb-8 flex items-start justify-between gap-4">
                <div>
                  <h2 className="text-2xl font-bold text-slate-900">{selectedDependentId ? "부양가족 수정" : "부양가족 추가"}</h2>
                  <p className="mt-2 text-sm text-slate-500">
                    1단계 확정 후에는 가장 위의 등록 데이터를 기본으로 보여주고, 잠금 상태가 더 눈에 띄게 표시됩니다.
                  </p>
                </div>
                <button
                  className={`rounded-xl border px-4 py-2.5 text-sm font-semibold transition-all ${
                    selectedDependentId && !isConfirmed
                      ? "border-red-200 bg-red-50 text-red-500 hover:border-red-300 hover:bg-red-100"
                      : "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400"
                  }`}
                  disabled={!selectedDependentId || isConfirmed || isSaving}
                  onClick={handleDelete}
                  type="button"
                >
                  <span className="material-symbols-outlined text-lg">delete</span>
                  삭제
                </button>
              </div>

              {isConfirmed ? (
                <div className="mb-6 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-800">
                  1단계가 확정되어 현재 입력은 잠겨 있습니다. 수정하려면 아래의 `1단계 확정 풀기`를 눌러 주세요.
                </div>
              ) : null}

              <MessageBanner message={message} />

              <form className="space-y-6" onSubmit={handleSave}>
                <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
                  <FieldWrapper disabled={isConfirmed}>
                    <label className="mb-2 block text-sm font-semibold text-slate-700" htmlFor="dependent-name">이름</label>
                    <input
                      className={`w-full rounded-lg border px-4 py-2.5 outline-none transition-all ${isConfirmed ? "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400" : "border-slate-200 focus:border-transparent focus:ring-2 focus:ring-primary"}`}
                      disabled={isConfirmed}
                      id="dependent-name"
                      onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
                      type="text"
                      value={form.name}
                    />
                  </FieldWrapper>

                  <FieldWrapper disabled={isConfirmed}>
                    <label className="mb-2 block text-sm font-semibold text-slate-700" htmlFor="dependent-relation">관계</label>
                    <select
                      className={`w-full rounded-lg border px-4 py-2.5 outline-none transition-all ${isConfirmed ? "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400" : "border-slate-200 focus:border-transparent focus:ring-2 focus:ring-primary"}`}
                      disabled={isConfirmed}
                      id="dependent-relation"
                      onChange={(event) => setForm((current) => ({ ...current, relationType: event.target.value }))}
                      value={form.relationType}
                    >
                      <option value="SELF">본인</option>
                      <option value="SPOUSE">배우자</option>
                      <option value="CHILD">자녀</option>
                      <option value="PARENT">부모</option>
                    </select>
                  </FieldWrapper>

                  <FieldWrapper disabled={isConfirmed}>
                    <label className="mb-2 block text-sm font-semibold text-slate-700" htmlFor="dependent-birth-date">생년월일</label>
                    <input
                      className={`w-full rounded-lg border px-4 py-2.5 outline-none transition-all ${isConfirmed ? "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400" : "border-slate-200 focus:border-transparent focus:ring-2 focus:ring-primary"}`}
                      disabled={isConfirmed}
                      id="dependent-birth-date"
                      onChange={(event) => setForm((current) => ({ ...current, birthDate: event.target.value }))}
                      type="date"
                      value={form.birthDate}
                    />
                  </FieldWrapper>

                  <FieldWrapper disabled={isConfirmed}>
                    <label className="mb-2 block text-sm font-semibold text-slate-700" htmlFor="dependent-income">연간 소득</label>
                    <input
                      className={`w-full rounded-lg border px-4 py-2.5 outline-none transition-all ${isConfirmed ? "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400" : "border-slate-200 focus:border-transparent focus:ring-2 focus:ring-primary"}`}
                      disabled={isConfirmed}
                      id="dependent-income"
                      min="0"
                      onChange={(event) => setForm((current) => ({ ...current, annualIncomeAmount: event.target.value }))}
                      step="1"
                      type="number"
                      value={form.annualIncomeAmount}
                    />
                  </FieldWrapper>
                </div>

                <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                  <FieldWrapper disabled={isConfirmed}>
                    <label className="mb-2 block text-sm font-semibold text-slate-700" htmlFor="dependent-resident-type">거주 구분</label>
                    <select
                      className={`w-full rounded-lg border px-4 py-2.5 outline-none transition-all ${isConfirmed ? "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400" : "border-slate-200 focus:border-transparent focus:ring-2 focus:ring-primary"}`}
                      disabled={isConfirmed}
                      id="dependent-resident-type"
                      onChange={(event) => setForm((current) => ({ ...current, residentType: event.target.value }))}
                      value={form.residentType}
                    >
                      <option value="RESIDENT">거주자</option>
                      <option value="NON_RESIDENT">비거주자</option>
                    </select>
                  </FieldWrapper>

                  <FieldWrapper disabled={isConfirmed}>
                    <label className="mb-2 block text-sm font-semibold text-slate-700">공제 조건</label>
                    <div className="grid grid-cols-1 gap-3 pt-2">
                      <label className="flex cursor-pointer items-center gap-2">
                        <input checked={form.livesTogether} className="h-4 w-4 text-primary focus:ring-primary" disabled={isConfirmed} onChange={(event) => setForm((current) => ({ ...current, livesTogether: event.target.checked }))} type="checkbox" />
                        <span className="text-sm text-slate-600">동거 중</span>
                      </label>
                      <label className="flex cursor-pointer items-center gap-2">
                        <input checked={form.disabled} className="h-4 w-4 text-primary focus:ring-primary" disabled={isConfirmed} onChange={(event) => setForm((current) => ({ ...current, disabled: event.target.checked }))} type="checkbox" />
                        <span className="text-sm text-slate-600">장애 대상</span>
                      </label>
                      <label className="flex cursor-pointer items-center gap-2">
                        <input checked={form.basicDeductionTarget} className="h-4 w-4 text-primary focus:ring-primary" disabled={isConfirmed} onChange={(event) => setForm((current) => ({ ...current, basicDeductionTarget: event.target.checked }))} type="checkbox" />
                        <span className="text-sm text-slate-600">기본공제 대상</span>
                      </label>
                    </div>
                  </FieldWrapper>
                </div>

                <div className="rounded-xl border border-primary/20 bg-primary/5 p-4">
                  <h3 className="text-sm font-bold text-primary">1단계 진행 안내</h3>
                  <p className="mt-2 text-sm text-slate-600">
                    부양가족을 저장한 뒤 `1단계 확정` 버튼을 눌러야 다음 단계인 소득 확인 화면이 진행 가능 상태가 됩니다.
                  </p>
                </div>

                <div className="flex items-center justify-between border-t border-slate-100 pt-6">
                  <button className="rounded-xl border border-slate-200 bg-white px-6 py-2.5 text-sm font-semibold text-slate-600 transition-all hover:border-slate-300 hover:bg-slate-50 hover:text-slate-900" onClick={() => startTransition(() => router.push("/"))} type="button">
                    대시보드
                  </button>
                  <div className="flex gap-4">
                    {!isConfirmed ? (
                      <button className="rounded-xl border border-slate-200 bg-white px-6 py-2.5 text-sm font-semibold text-slate-700 transition-all hover:border-slate-300 hover:bg-slate-50 disabled:opacity-60" disabled={isSaving} type="submit">
                        {isSaving ? "저장 중..." : "저장"}
                      </button>
                    ) : null}
                    <button
                      className={`rounded-xl px-6 py-2.5 text-sm font-semibold text-white transition-all ${
                        isConfirmed ? "bg-red-500 hover:bg-red-600" : "bg-primary hover:bg-primary/90"
                      }`}
                      disabled={isSaving}
                      onClick={handleConfirmStep}
                      type="button"
                    >
                      {isConfirmed ? "1단계 확정 풀기" : "1단계 확정"}
                    </button>
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
