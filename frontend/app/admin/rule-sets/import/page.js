"use client";

import Link from "next/link";
import { useRef, useState } from "react";
import { getAccessToken, importLawPack } from "@/lib/yearEndApi";

const STATUS_LABELS = {
  DRAFT: "초안",
  READY_FOR_REVIEW: "검토 대기",
  BLOCKED: "보류",
  PUBLISHED: "게시됨",
  RETIRED: "회수됨"
};

function StatusBadge({ status }) {
  const tone = status === "PUBLISHED"
    ? "bg-emerald-100 text-emerald-700"
    : status === "BLOCKED"
      ? "bg-red-100 text-red-700"
      : "bg-amber-100 text-amber-700";
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-1 text-[11px] font-semibold ${tone}`}>
      {STATUS_LABELS[status] || status || "-"}
    </span>
  );
}

function MessageBanner({ message }) {
  if (!message) return null;
  const tone = message.type === "success"
    ? "border-emerald-200 bg-emerald-50 text-emerald-700"
    : "border-red-200 bg-red-50 text-red-700";
  return <div className={`rounded-2xl border px-4 py-3 text-sm ${tone}`}>{message.text}</div>;
}

export default function AdminRuleSetImportPage() {
  const fileInputRef = useRef(null);
  const [selectedFile, setSelectedFile] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState(null);
  const [result, setResult] = useState(null);

  const handleFileChange = (event) => {
    const file = event.target.files?.[0] || null;
    setSelectedFile(file);
    setResult(null);
    setMessage(null);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!selectedFile) {
      setMessage({ type: "error", text: "임포트할 normalized-rule-pack.json 파일을 선택하세요." });
      return;
    }
    if (!getAccessToken()) {
      setMessage({ type: "error", text: "로그인이 필요합니다. 관리자 계정으로 로그인 후 다시 시도하세요." });
      return;
    }

    setSubmitting(true);
    setMessage(null);
    setResult(null);
    try {
      const data = await importLawPack(selectedFile);
      setResult(data);
      setMessage({
        type: "success",
        text: `임포트 완료: ${data.importedRuleCount}개 규칙이 DRAFT로 저장되었습니다.`
      });
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
      setSelectedFile(null);
    } catch (error) {
      setMessage({
        type: "error",
        text: error.message || "임포트 중 알 수 없는 오류가 발생했습니다."
      });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="mx-auto max-w-3xl px-4 py-10 sm:px-6 lg:px-8">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <p className="text-sm font-semibold text-primary">관리자 · 룰셋</p>
          <h1 className="mt-1 text-2xl font-black text-slate-900">법령팩 임포트</h1>
          <p className="mt-2 text-sm leading-6 text-slate-600">
            하네스에서 만든 <code className="rounded bg-slate-100 px-1.5 py-0.5 text-[12px]">normalized-rule-pack.json</code> 파일을
            업로드하면 DRAFT 룰셋으로 저장됩니다. 게시(PUBLISHED) 전환은 별도 검토/승인 절차를 거쳐야 합니다.
          </p>
        </div>
        <Link
          href="/"
          className="text-sm font-semibold text-primary hover:underline"
        >
          ← 메인으로
        </Link>
      </div>

      <form
        onSubmit={handleSubmit}
        className="space-y-5 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"
      >
        <div>
          <label htmlFor="lawpack" className="block text-sm font-semibold text-slate-700">
            법령팩 JSON 파일
          </label>
          <input
            ref={fileInputRef}
            id="lawpack"
            type="file"
            accept="application/json,.json"
            onChange={handleFileChange}
            className="mt-2 block w-full cursor-pointer rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-3 text-sm text-slate-700 file:mr-4 file:rounded-lg file:border-0 file:bg-primary file:px-4 file:py-2 file:text-sm file:font-semibold file:text-white hover:border-primary"
          />
          {selectedFile ? (
            <p className="mt-2 text-xs text-slate-500">
              선택됨: <span className="font-semibold text-slate-700">{selectedFile.name}</span>
              {" · "}
              {Math.round(selectedFile.size / 1024)} KB
            </p>
          ) : (
            <p className="mt-2 text-xs text-slate-400">
              예: <code>plugins/year-end-harness/law-packs/2025/2025.01/normalized-rule-pack.json</code>
            </p>
          )}
        </div>

        <MessageBanner message={message} />

        <div className="flex items-center gap-3">
          <button
            type="submit"
            disabled={submitting || !selectedFile}
            className="inline-flex items-center justify-center rounded-xl bg-primary px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {submitting ? "임포트 중..." : "DRAFT로 임포트"}
          </button>
          <p className="text-xs text-slate-500">
            동일 (taxYear, ruleVersion) DRAFT가 이미 있으면 기존 규칙을 교체합니다. 검토/게시된 룰셋은 충돌로 거부됩니다.
          </p>
        </div>
      </form>

      {result ? (
        <section className="mt-8 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-bold text-slate-900">임포트 결과</h2>
          <dl className="mt-4 grid grid-cols-1 gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-slate-500">룰셋 ID</dt>
              <dd className="mt-1 break-all font-mono text-xs text-slate-700">{result.ruleSetId}</dd>
            </div>
            <div>
              <dt className="text-slate-500">상태</dt>
              <dd className="mt-1"><StatusBadge status={result.status} /></dd>
            </div>
            <div>
              <dt className="text-slate-500">과세 연도</dt>
              <dd className="mt-1 font-semibold text-slate-900">{result.taxYear}</dd>
            </div>
            <div>
              <dt className="text-slate-500">룰 버전</dt>
              <dd className="mt-1 font-semibold text-slate-900">{result.ruleVersion}</dd>
            </div>
            <div>
              <dt className="text-slate-500">임포트된 규칙</dt>
              <dd className="mt-1 text-lg font-black text-primary">{result.importedRuleCount}개</dd>
            </div>
            <div>
              <dt className="text-slate-500">교체된 기존 규칙</dt>
              <dd className="mt-1 font-semibold text-slate-700">{result.replacedRuleCount}개</dd>
            </div>
          </dl>
          <div className="mt-5 rounded-xl bg-slate-50 p-4 text-xs leading-5 text-slate-600">
            <p className="font-semibold text-slate-700">다음 단계</p>
            <ol className="mt-1 list-decimal pl-5">
              <li>DRAFT 룰셋을 검토하여 READY_FOR_REVIEW로 전환</li>
              <li>관리자 검토 (승인/반려) 후 PUBLISHED로 게시</li>
              <li>계산 엔진은 PUBLISHED 룰셋만 읽어 적용</li>
            </ol>
          </div>
        </section>
      ) : null}
    </main>
  );
}
