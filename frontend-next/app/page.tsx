"use client";

import { useState } from "react";

import { LoginPanel } from "@/components/auth/login-panel";
import { VerificationPanel } from "@/components/auth/verification-panel";
import { Alert } from "@/components/common/alert";
import { useAuth } from "@/hooks/use-auth";

export default function Home() {
  const auth = useAuth();
  const [verificationError, setVerificationError] = useState<string | null>(
    null,
  );
  const message = verificationError ?? auth.message;

  return (
    <div className="min-h-screen overflow-hidden bg-background text-foreground">
      <header className="sticky top-0 z-50 grid min-h-[70px] grid-cols-[auto_1fr_auto] items-center border-b border-border bg-background/92 px-[5vw] shadow-[0_1px_12px_rgb(0_0_0/4%)] backdrop-blur-2xl">
        <button
          aria-label="Re:Seat 홈"
          className="cursor-pointer border-0 bg-transparent p-0 text-[28px] font-black tracking-[-1.5px] text-foreground"
          type="button"
        >
          Re:<span className="text-brand">Seat</span>
        </button>
        <nav
          aria-label="주요 메뉴"
          className="flex h-[70px] justify-center gap-[30px]"
        >
          <button
            className="relative cursor-pointer border-0 bg-transparent px-1 text-sm font-bold text-foreground after:absolute after:right-0 after:bottom-0 after:left-0 after:h-0.5 after:bg-brand after:content-['']"
            type="button"
          >
            경기 예매
          </button>
          <button
            className="cursor-pointer border-0 bg-transparent px-1 text-sm font-bold text-muted-foreground disabled:cursor-not-allowed disabled:opacity-50"
            disabled={!auth.isAuthed}
            type="button"
          >
            내 티켓
          </button>
        </nav>
        <LoginPanel
          isAuthed={auth.isAuthed}
          onLogin={auth.login}
          onLogout={auth.logout}
          profile={auth.profile}
          role={auth.role}
        />
      </header>

      {message && (
        <Alert
          message={message}
          onClose={() => {
            setVerificationError(null);
            auth.dismissMessage();
          }}
          variant={verificationError ? "error" : auth.messageVariant}
        />
      )}

      {auth.isAuthed && !auth.isVerified ? (
        <VerificationPanel
          busy={auth.busy}
          onError={setVerificationError}
          onLogout={auth.logout}
          onVerify={auth.verify}
        />
      ) : (
        <main className="flex min-h-[650px] items-center justify-center px-6 py-16">
          <section className="w-full max-w-2xl rounded-panel border border-border bg-surface p-8 shadow-card sm:p-12">
            <p className="mb-3 text-sm font-semibold text-brand">
              Re:Seat Frontend Migration
            </p>
            <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">
              경기 예매 화면을 준비하고 있습니다
            </h1>
            <p className="mt-5 leading-7 text-muted-foreground">
              인증 흐름을 먼저 이전했습니다. 다음 단계에서 기존 경기 목록과
              예매 시작 화면을 동일하게 옮깁니다.
            </p>
          </section>
        </main>
      )}
    </div>
  );
}
