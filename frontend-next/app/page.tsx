const legacyFrontendUrl =
  process.env.NEXT_PUBLIC_LEGACY_FRONTEND_URL ?? "http://localhost:5173";

/**
 * 실제 화면을 옮기기 전에 Next.js 실행 환경과 디자인 토큰을 확인하는 임시 화면입니다.
 * 사용자 기능은 이후 작업에서 화면 단위로 이전하며, 그전까지 기존 Vite 앱을 기준선으로 사용합니다.
 */
export default function Home() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-background px-6 py-16 text-foreground">
      <section className="w-full max-w-2xl rounded-panel border border-border bg-surface p-8 shadow-card sm:p-12">
        <p className="mb-3 text-sm font-semibold text-brand">
          Re:Seat Frontend Migration
        </p>
        <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">
          마이그레이션 준비 중
        </h1>
        <p className="mt-5 leading-7 text-muted-foreground">
          기존 사용자 흐름을 유지하면서 Next.js 기반 화면을 단계적으로
          옮기고 있습니다. 현재 서비스는 기존 Vite 앱에서 확인할 수 있습니다.
        </p>
        <a
          className="mt-8 inline-flex min-h-11 items-center justify-center rounded-control bg-brand px-5 font-semibold text-white transition-colors duration-fast hover:bg-brand-dark focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
          href={legacyFrontendUrl}
        >
          기존 Vite 앱 열기
        </a>
      </section>
    </main>
  );
}
