"use client";

import { Suspense, useEffect, useMemo, useState, startTransition } from "react";
import { useRouter, useSearchParams } from "next/navigation";

import {
  clearAuth,
  exchangeSocialCode,
  getAccessToken,
  getSocialAuthorizeUrl,
  initializeAuthenticatedContext,
  persistAuthTokens,
  request
} from "@/lib/yearEndApi";

const DEFAULT_ERRORS = {
  loginEmail: "",
  loginPassword: "",
  signupName: "",
  signupNickname: "",
  signupEmail: "",
  signupPassword: ""
};

function AuthMessage({ message }) {
  if (!message) {
    return null;
  }

  const toneClasses = message.type === "success"
    ? "border-emerald-200 bg-emerald-50 text-emerald-700"
    : "border-red-200 bg-red-50 text-red-700";

  return <div className={`mb-4 rounded-lg border px-4 py-3 text-sm ${toneClasses}`}>{message.text}</div>;
}

function FieldError({ children }) {
  return <p className="min-h-5 text-sm text-red-600">{children}</p>;
}

function getFriendlyErrorMessage(payload, fallback) {
  const code = payload?.error?.code;

  if (code === "AUTH_409") return "이미 가입된 이메일입니다.";
  if (code === "AUTH_401" || code === "AUTH_401_1") return "이메일 또는 비밀번호가 올바르지 않습니다.";
  if (code === "COMMON_422") return "입력값을 다시 확인해 주세요.";

  return payload?.error?.message || fallback;
}

function applyServerFieldErrors(fieldErrors, prefix, setErrors) {
  if (!Array.isArray(fieldErrors)) {
    return;
  }

  setErrors((current) => {
    const next = { ...current };

    fieldErrors.forEach((item) => {
      if (!item?.field || !item?.reason) {
        return;
      }

      next[`${prefix}${item.field.charAt(0).toUpperCase()}${item.field.slice(1)}`] = item.reason;
    });

    return next;
  });
}

function buildRedirectUri() {
  if (typeof window === "undefined") {
    return "";
  }
  return `${window.location.origin}/auth`;
}

function buildProviderRedirectUri(provider) {
  return `${buildRedirectUri()}?provider=${provider}`;
}

function AuthPageContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [activeTab, setActiveTab] = useState("login");
  const [message, setMessage] = useState(null);
  const [errors, setErrors] = useState(DEFAULT_ERRORS);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [socialLoadingProvider, setSocialLoadingProvider] = useState("");
  const [showLoginPassword, setShowLoginPassword] = useState(false);
  const [showSignupPassword, setShowSignupPassword] = useState(false);
  const [loginForm, setLoginForm] = useState({
    email: "",
    password: "",
    remember: false
  });
  const [signupForm, setSignupForm] = useState({
    name: "",
    nickname: "",
    email: "",
    password: ""
  });

  const socialCode = searchParams.get("code");
  const socialState = searchParams.get("state");
  const socialProvider = useMemo(() => {
    const provider = searchParams.get("provider");
    if (provider === "kakao" || provider === "naver") {
      return provider;
    }
    return "";
  }, [searchParams]);

  useEffect(() => {
    if (!getAccessToken()) {
      return;
    }

    let active = true;

    initializeAuthenticatedContext()
      .then(() => {
        if (!active) {
          return;
        }

        startTransition(() => {
          router.replace("/");
        });
      })
      .catch(() => {
        if (!active) {
          return;
        }

        clearAuth();
      });

    return () => {
      active = false;
    };
  }, [router]);

  useEffect(() => {
    if (!socialProvider || !socialCode) {
      return;
    }

    let active = true;

    (async () => {
      setMessage(null);
      setSocialLoadingProvider(socialProvider);

      try {
        const data = await exchangeSocialCode(socialProvider, {
          code: socialCode,
          state: socialState,
          redirectUri: buildProviderRedirectUri(socialProvider)
        });

        if (!active) {
          return;
        }

        persistAuthTokens(data);
        await initializeAuthenticatedContext();
        startTransition(() => {
          router.replace("/");
        });
      } catch (error) {
        if (!active) {
          return;
        }

        setMessage({
          type: "error",
          text: getFriendlyErrorMessage(error?.payload, `${socialProvider} 로그인 연동에 실패했습니다.`)
        });
      } finally {
        if (active) {
          setSocialLoadingProvider("");
        }
      }
    })();

    return () => {
      active = false;
    };
  }, [router, socialCode, socialProvider, socialState]);

  function resetFeedback(nextTab) {
    setActiveTab(nextTab);
    setErrors(DEFAULT_ERRORS);
    setMessage(null);
  }

  function isValidEmail(value) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
  }

  async function callAuth(endpoint, payload) {
    return request(endpoint, {
      method: "POST",
      body: payload
    }, {
      auth: false
    });
  }

  async function handleLogin(event) {
    event.preventDefault();
    setErrors(DEFAULT_ERRORS);
    setMessage(null);

    const nextErrors = { ...DEFAULT_ERRORS };
    let valid = true;

    if (!loginForm.email.trim()) {
      nextErrors.loginEmail = "이메일을 입력해 주세요.";
      valid = false;
    } else if (!isValidEmail(loginForm.email.trim())) {
      nextErrors.loginEmail = "올바른 이메일 형식을 입력해 주세요.";
      valid = false;
    }

    if (!loginForm.password.trim()) {
      nextErrors.loginPassword = "비밀번호를 입력해 주세요.";
      valid = false;
    }

    if (!valid) {
      setErrors(nextErrors);
      return;
    }

    setIsSubmitting(true);

    try {
      const data = await callAuth("/api/v1/auth/login", {
        email: loginForm.email.trim(),
        password: loginForm.password
      });

      persistAuthTokens(data);
      await initializeAuthenticatedContext();
      setMessage({
        type: "success",
        text: "로그인에 성공했습니다. 대시보드로 이동합니다."
      });
      window.setTimeout(() => {
        startTransition(() => {
          router.replace("/");
        });
      }, 250);
    } catch (error) {
      applyServerFieldErrors(error?.payload?.error?.fieldErrors, "login", setErrors);
      setMessage({
        type: "error",
        text: getFriendlyErrorMessage(error?.payload, "로그인에 실패했습니다. 다시 시도해 주세요.")
      });
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleSignup(event) {
    event.preventDefault();
    setErrors(DEFAULT_ERRORS);
    setMessage(null);

    const nextErrors = { ...DEFAULT_ERRORS };
    let valid = true;

    if (!signupForm.name.trim()) {
      nextErrors.signupName = "이름을 입력해 주세요.";
      valid = false;
    }

    if (!signupForm.nickname.trim()) {
      nextErrors.signupNickname = "닉네임을 입력해 주세요.";
      valid = false;
    }

    if (!signupForm.email.trim()) {
      nextErrors.signupEmail = "이메일을 입력해 주세요.";
      valid = false;
    } else if (!isValidEmail(signupForm.email.trim())) {
      nextErrors.signupEmail = "올바른 이메일 형식을 입력해 주세요.";
      valid = false;
    }

    if (!signupForm.password.trim()) {
      nextErrors.signupPassword = "비밀번호를 입력해 주세요.";
      valid = false;
    } else if (signupForm.password.length < 8) {
      nextErrors.signupPassword = "비밀번호는 8자 이상이어야 합니다.";
      valid = false;
    }

    if (!valid) {
      setErrors(nextErrors);
      return;
    }

    setIsSubmitting(true);

    try {
      const data = await callAuth("/api/v1/auth/signup", {
        name: signupForm.name.trim(),
        nickname: signupForm.nickname.trim(),
        email: signupForm.email.trim(),
        password: signupForm.password
      });

      persistAuthTokens(data);
      await initializeAuthenticatedContext();
      setMessage({
        type: "success",
        text: "회원가입이 완료되었습니다. 대시보드로 이동합니다."
      });
      window.setTimeout(() => {
        startTransition(() => {
          router.replace("/");
        });
      }, 250);
    } catch (error) {
      applyServerFieldErrors(error?.payload?.error?.fieldErrors, "signup", setErrors);
      setMessage({
        type: "error",
        text: getFriendlyErrorMessage(error?.payload, "회원가입에 실패했습니다. 다시 시도해 주세요.")
      });
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleSocialLogin(provider) {
    setMessage(null);
    setSocialLoadingProvider(provider);

    try {
      const state = crypto.randomUUID();
      const data = await getSocialAuthorizeUrl(provider, buildProviderRedirectUri(provider), state);
      window.location.href = data.authorizeUrl;
    } catch (error) {
      setMessage({
        type: "error",
        text: getFriendlyErrorMessage(error?.payload, `${provider} 로그인 설정을 확인해 주세요.`)
      });
      setSocialLoadingProvider("");
    }
  }

  return (
    <div className="flex min-h-screen flex-col bg-background-light font-display dark:bg-background-dark">
      <div className="relative flex min-h-screen w-full flex-col overflow-x-hidden bg-background-light dark:bg-background-dark">
        <div className="flex grow flex-col">
          <header className="flex items-center justify-between whitespace-nowrap border-b border-slate-200 bg-white px-6 py-3 dark:border-slate-800 dark:bg-slate-900 md:px-10">
            <div className="flex items-center gap-4 text-slate-900 dark:text-slate-100">
              <div className="size-8 text-primary">
                <svg fill="none" viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg">
                  <path
                    d="M24 45.8096C19.6865 45.8096 15.4698 44.5305 11.8832 42.134C8.29667 39.7376 5.50128 36.3314 3.85056 32.3462C2.19985 28.361 1.76794 23.9758 2.60947 19.7452C3.451 15.5145 5.52816 11.6284 8.57829 8.5783C11.6284 5.52817 15.5145 3.45101 19.7452 2.60948C23.9758 1.76795 28.361 2.19986 32.3462 3.85057C36.3314 5.50129 39.7376 8.29668 42.134 11.8833C44.5305 15.4698 45.8096 19.6865 45.8096 24L24 24L24 45.8096Z"
                    fill="currentColor"
                  />
                </svg>
              </div>
              <h2 className="text-xl font-bold leading-tight tracking-[-0.015em] text-slate-900 dark:text-white">
                Ligg-Tax
              </h2>
            </div>
            <button
              className="flex h-10 min-w-[84px] cursor-pointer items-center justify-center rounded-lg bg-primary px-4 text-sm font-bold text-white transition-colors hover:bg-primary/90"
              type="button"
            >
              <span className="truncate">고객 센터</span>
            </button>
          </header>

          <main className="flex flex-1 items-center justify-center p-4 md:p-8">
            <div className="w-full max-w-[520px] overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xl dark:border-slate-800 dark:bg-slate-900">
              <div className="p-8 pb-4">
                <div className="mb-8 flex flex-col gap-2">
                  <h1 className="text-3xl font-black leading-tight tracking-[-0.033em] text-slate-900 dark:text-white">
                    로그인 및 회원가입
                  </h1>
                  <p className="text-base text-slate-500 dark:text-slate-400">
                    연말정산 진행을 쉽게 이어가 보세요.
                  </p>
                </div>

                <div className="pb-6">
                  <div className="flex gap-8 border-b border-slate-200 px-0 dark:border-slate-800">
                    {["login", "signup"].map((tab) => {
                      const active = activeTab === tab;

                      return (
                        <button
                          key={tab}
                          className={[
                            "flex flex-col items-center justify-center border-b-[3px] pb-[13px] pt-2 text-sm font-bold transition-colors",
                            active
                              ? "border-primary text-slate-900 dark:text-white"
                              : "border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
                          ].join(" ")}
                          onClick={() => resetFeedback(tab)}
                          type="button"
                        >
                          <span>{tab === "login" ? "로그인" : "회원가입"}</span>
                        </button>
                      );
                    })}
                  </div>
                </div>

                <AuthMessage message={message} />

                {activeTab === "login" ? (
                  <form className="space-y-4" noValidate onSubmit={handleLogin}>
                    <div className="flex flex-col gap-1.5">
                      <label className="text-sm font-semibold text-slate-900 dark:text-slate-100" htmlFor="login-email">
                        이메일
                      </label>
                      <input
                        autoComplete="email"
                        className="form-input h-12 w-full rounded-lg border-slate-200 px-4 text-base text-slate-900 focus:border-primary focus:ring-1 focus:ring-primary dark:border-slate-700 dark:bg-slate-800 dark:text-white"
                        id="login-email"
                        onChange={(event) => setLoginForm((current) => ({ ...current, email: event.target.value }))}
                        placeholder="이메일 주소를 입력하세요"
                        type="email"
                        value={loginForm.email}
                      />
                      <FieldError>{errors.loginEmail}</FieldError>
                    </div>

                    <div className="flex flex-col gap-1.5">
                      <label className="text-sm font-semibold text-slate-900 dark:text-slate-100" htmlFor="login-password">
                        비밀번호
                      </label>
                      <div className="relative flex w-full items-center">
                        <input
                          autoComplete="current-password"
                          className="form-input h-12 w-full rounded-lg border-slate-200 px-4 pr-12 text-base text-slate-900 focus:border-primary focus:ring-1 focus:ring-primary dark:border-slate-700 dark:bg-slate-800 dark:text-white"
                          id="login-password"
                          onChange={(event) => setLoginForm((current) => ({ ...current, password: event.target.value }))}
                          placeholder="비밀번호를 입력하세요"
                          type={showLoginPassword ? "text" : "password"}
                          value={loginForm.password}
                        />
                        <button
                          className="absolute right-3 text-slate-400 transition-colors hover:text-slate-600 dark:text-slate-500 dark:hover:text-slate-300"
                          onClick={() => setShowLoginPassword((current) => !current)}
                          type="button"
                        >
                          <span className="material-symbols-outlined text-[22px]">
                            {showLoginPassword ? "visibility_off" : "visibility"}
                          </span>
                        </button>
                      </div>
                      <FieldError>{errors.loginPassword}</FieldError>
                    </div>

                    <div className="flex items-center justify-between py-1">
                      <label className="flex cursor-pointer items-center gap-2">
                        <input
                          checked={loginForm.remember}
                          className="rounded border-slate-300 text-primary focus:ring-primary"
                          onChange={(event) => setLoginForm((current) => ({ ...current, remember: event.target.checked }))}
                          type="checkbox"
                        />
                        <span className="text-sm text-slate-600 dark:text-slate-400">로그인 상태 유지</span>
                      </label>
                    </div>

                    <button
                      className="mt-4 flex h-12 w-full items-center justify-center rounded-lg bg-primary px-4 text-base font-bold text-white shadow-lg shadow-primary/20 transition-all hover:bg-primary/90 disabled:opacity-60"
                      disabled={isSubmitting || Boolean(socialLoadingProvider)}
                      type="submit"
                    >
                      <span>{isSubmitting ? "로그인 중..." : "로그인"}</span>
                    </button>
                  </form>
                ) : (
                  <form className="space-y-4" noValidate onSubmit={handleSignup}>
                    <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                      <div className="flex flex-col gap-1.5">
                        <label className="text-sm font-semibold text-slate-900 dark:text-slate-100" htmlFor="signup-name">
                          이름
                        </label>
                        <input
                          autoComplete="name"
                          className="form-input h-12 w-full rounded-lg border-slate-200 px-4 text-base text-slate-900 focus:border-primary focus:ring-1 focus:ring-primary dark:border-slate-700 dark:bg-slate-800 dark:text-white"
                          id="signup-name"
                          onChange={(event) => setSignupForm((current) => ({ ...current, name: event.target.value }))}
                          placeholder="이름 입력"
                          type="text"
                          value={signupForm.name}
                        />
                        <FieldError>{errors.signupName}</FieldError>
                      </div>
                      <div className="flex flex-col gap-1.5">
                        <label className="text-sm font-semibold text-slate-900 dark:text-slate-100" htmlFor="signup-nickname">
                          닉네임
                        </label>
                        <input
                          className="form-input h-12 w-full rounded-lg border-slate-200 px-4 text-base text-slate-900 focus:border-primary focus:ring-1 focus:ring-primary dark:border-slate-700 dark:bg-slate-800 dark:text-white"
                          id="signup-nickname"
                          onChange={(event) => setSignupForm((current) => ({ ...current, nickname: event.target.value }))}
                          placeholder="닉네임 입력"
                          type="text"
                          value={signupForm.nickname}
                        />
                        <FieldError>{errors.signupNickname}</FieldError>
                      </div>
                    </div>

                    <div className="flex flex-col gap-1.5">
                      <label className="text-sm font-semibold text-slate-900 dark:text-slate-100" htmlFor="signup-email">
                        이메일
                      </label>
                      <input
                        autoComplete="email"
                        className="form-input h-12 w-full rounded-lg border-slate-200 px-4 text-base text-slate-900 focus:border-primary focus:ring-1 focus:ring-primary dark:border-slate-700 dark:bg-slate-800 dark:text-white"
                        id="signup-email"
                        onChange={(event) => setSignupForm((current) => ({ ...current, email: event.target.value }))}
                        placeholder="이메일 주소를 입력하세요"
                        type="email"
                        value={signupForm.email}
                      />
                      <FieldError>{errors.signupEmail}</FieldError>
                    </div>

                    <div className="flex flex-col gap-1.5">
                      <label className="text-sm font-semibold text-slate-900 dark:text-slate-100" htmlFor="signup-password">
                        비밀번호
                      </label>
                      <div className="relative flex w-full items-center">
                        <input
                          autoComplete="new-password"
                          className="form-input h-12 w-full rounded-lg border-slate-200 px-4 pr-12 text-base text-slate-900 focus:border-primary focus:ring-1 focus:ring-primary dark:border-slate-700 dark:bg-slate-800 dark:text-white"
                          id="signup-password"
                          onChange={(event) => setSignupForm((current) => ({ ...current, password: event.target.value }))}
                          placeholder="비밀번호를 입력하세요"
                          type={showSignupPassword ? "text" : "password"}
                          value={signupForm.password}
                        />
                        <button
                          className="absolute right-3 text-slate-400 transition-colors hover:text-slate-600 dark:text-slate-500 dark:hover:text-slate-300"
                          onClick={() => setShowSignupPassword((current) => !current)}
                          type="button"
                        >
                          <span className="material-symbols-outlined text-[22px]">
                            {showSignupPassword ? "visibility_off" : "visibility"}
                          </span>
                        </button>
                      </div>
                      <FieldError>{errors.signupPassword}</FieldError>
                    </div>

                    <button
                      className="mt-4 flex h-12 w-full items-center justify-center rounded-lg bg-primary px-4 text-base font-bold text-white shadow-lg shadow-primary/20 transition-all hover:bg-primary/90 disabled:opacity-60"
                      disabled={isSubmitting || Boolean(socialLoadingProvider)}
                      type="submit"
                    >
                      <span>{isSubmitting ? "회원가입 중..." : "회원가입"}</span>
                    </button>
                  </form>
                )}

                <div className="relative flex items-center gap-4 py-8">
                  <div className="flex-grow border-t border-slate-200 dark:border-slate-800" />
                  <span className="text-xs font-bold uppercase tracking-widest text-slate-400 dark:text-slate-500">
                    또는 소셜 계정으로 계속하기
                  </span>
                  <div className="flex-grow border-t border-slate-200 dark:border-slate-800" />
                </div>

                <div className="grid grid-cols-2 gap-4 pb-4">
                  <button
                    className="flex h-12 items-center justify-center gap-2 rounded-lg bg-[#FEE500] text-sm font-bold text-[#191919] transition-all hover:brightness-95 disabled:opacity-60"
                    disabled={Boolean(socialLoadingProvider)}
                    onClick={() => void handleSocialLogin("kakao")}
                    type="button"
                  >
                    <span className="material-symbols-outlined text-[20px]">chat</span>
                    {socialLoadingProvider === "kakao" ? "연동 중..." : "카카오"}
                  </button>
                  <button
                    className="flex h-12 items-center justify-center gap-2 rounded-lg bg-[#03C75A] text-sm font-bold text-white transition-all hover:brightness-95 disabled:opacity-60"
                    disabled={Boolean(socialLoadingProvider)}
                    onClick={() => void handleSocialLogin("naver")}
                    type="button"
                  >
                    <span className="material-symbols-outlined text-[20px]">circle</span>
                    {socialLoadingProvider === "naver" ? "연동 중..." : "네이버"}
                  </button>
                </div>
              </div>

              <div className="border-t border-slate-200 bg-slate-50 p-6 text-center dark:border-slate-800 dark:bg-slate-800/50">
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  Ligg-Tax가 처음이신가요?{" "}
                  <button
                    className="font-bold text-primary hover:underline"
                    onClick={() => resetFeedback("signup")}
                    type="button"
                  >
                    계정 만들기
                  </button>
                </p>
              </div>
            </div>
          </main>
        </div>
      </div>
    </div>
  );
}

export default function AuthPage() {
  return (
    <Suspense fallback={<div className="flex min-h-screen items-center justify-center bg-background-light text-slate-500">인증 화면을 준비하는 중입니다...</div>}>
      <AuthPageContent />
    </Suspense>
  );
}
