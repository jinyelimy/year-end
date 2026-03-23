"use client";

import Link from "next/link";

function getAvatarText(user) {
  return (user?.name || "Y").slice(0, 1).toUpperCase();
}

function getDisplayName(user) {
  return `${user?.name || "사용자"}님`;
}

export default function StageThreeSidebar({
  user,
  session,
  activeStep,
  isConfirmed,
  importedCount = 0,
  manualCount = 0
}) {
  const navItems = [
    {
      key: "import-data",
      href: "/import-data",
      icon: "cloud_sync",
      label: "간소화 자료"
    },
    {
      key: "deductions",
      href: "/deductions",
      icon: "receipt_long",
      label: "공제 항목 입력"
    }
  ];

  return (
    <div className="sticky top-24 space-y-6">
      <div className="rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm">
        <div className="mb-7 flex items-center gap-4">
          <div className="flex h-12 w-12 items-center justify-center rounded-full bg-primary text-lg font-bold text-white">
            {getAvatarText(user)}
          </div>
          <div>
            <h2 className="text-lg font-bold leading-tight text-slate-900">{getDisplayName(user)}</h2>
            <p className="mt-1 text-sm font-medium text-slate-500">
              {isConfirmed ? "3단계 완료" : "3단계 진행 중"}
            </p>
          </div>
        </div>

        <nav className="space-y-2">
          {navItems.map((item) => {
            const selected = item.key === activeStep;

            return (
              <Link
                key={item.key}
                className={`flex items-center gap-3 rounded-xl px-4 py-3 transition-colors ${
                  selected
                    ? "bg-primary/10 text-primary"
                    : "text-slate-600 hover:bg-slate-50"
                }`}
                href={item.href}
              >
                <span className="material-symbols-outlined text-[1.9rem]">{item.icon}</span>
                <span className="text-sm font-semibold">{item.label}</span>
              </Link>
            );
          })}
        </nav>
      </div>

      <div className="rounded-2xl border border-primary/20 bg-primary/5 p-5">
        <h3 className="text-sm font-bold text-primary">3단계 안내</h3>
        <p className="mt-2 text-sm leading-6 text-slate-600">
          간소화 자료를 확인하고 공제 항목을 입력한 뒤 3단계를 확정하면 다음 단계인 증빙 서류 관리가 열립니다.
        </p>
        <div className="mt-4 space-y-2 text-xs text-slate-500">
          <p>세션 연도: {session?.taxYear}년 귀속</p>
          <p>간소화 자료: {importedCount}건</p>
          <p>직접 입력 공제: {manualCount}건</p>
        </div>
      </div>
    </div>
  );
}
