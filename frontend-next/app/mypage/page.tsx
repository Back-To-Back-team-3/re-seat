"use client";

import Link from "next/link";

import {Alert} from "@/components/common/alert";
import {ProfileSection} from "@/components/mypage/profile-section";
import {TicketList} from "@/components/tickets/ticket-list";
import {useAuth} from "@/hooks/use-auth";
import {useGames} from "@/hooks/use-games";
import {useCancelTicket, useRetryCancelTicket, useTickets} from "@/hooks/use-tickets";

export default function MyPage() {
    const auth = useAuth();
    const tickets = useTickets(auth.isAuthed);
    const games = useGames();
    const cancelTicketMutation = useCancelTicket();
    const retryCancelTicketMutation = useRetryCancelTicket();

    if (!auth.isAuthed) {
        return (
            <main className="grid min-h-[60vh] place-items-center p-6">
                <section className="text-center">
                    <h1 className="text-2xl font-bold">로그인이 필요합니다.</h1>
                    <p className="mt-2 text-sm text-muted-foreground">
                        마이페이지 및 티켓 관리를 위해 먼저 로그인해 주세요.
                    </p>
                    <button
                        className="mt-6 inline-flex min-h-11 items-center justify-center rounded-control bg-brand px-6 text-sm font-bold text-white transition hover:bg-brand/90"
                        onClick={auth.login}
                        type="button"
                    >
                        로그인하기
                    </button>
                    <div className="mt-4">
                        <Link className="text-xs text-muted-foreground hover:underline" href="/games">
                            경기 목록으로 돌아가기
                        </Link>
                    </div>
                </section>
            </main>
        );
    }

    return (
        <main className="relative mx-auto min-h-[650px] w-full max-w-[var(--width-shell)] px-[var(--gutter-desktop)] pt-12 pb-[90px] max-sm:px-[var(--gutter-mobile)] max-sm:pt-[46px] max-sm:pb-[70px]">
            <div className="mx-auto mb-8 w-full max-w-[1120px]">
                <span className="inline-block text-xs font-extrabold tracking-[0.1em] text-brand">
                    MY PAGE
                </span>
                <h1 className="mt-1 mb-1 text-[clamp(28px,3.5vw,42px)] font-black tracking-[-0.04em]">
                    마이페이지
                </h1>
                <p className="text-sm text-muted-foreground">
                    계정 정보를 확인하고 보유 티켓 및 환불을 관리합니다.
                </p>
            </div>

            {auth.message && (
                <div className="mx-auto mb-6 max-w-[1120px]">
                    <Alert
                        message={auth.message}
                        onClose={auth.dismissMessage}
                        variant={auth.messageVariant}
                    />
                </div>
            )}

            {/* 회원 프로필 & 회원 탈퇴 섹션 */}
            <ProfileSection
                isVerified={auth.isVerified}
                isWithdrawing={auth.isWithdrawing}
                onLogout={auth.logout}
                onWithdraw={auth.withdraw}
                profile={auth.profile}
                role={auth.role}
            />

            {/* 티켓 목록 & 환불 섹션 */}
            {tickets.error && (
                <div className="mx-auto mb-4 max-w-[1120px]">
                    <Alert message={tickets.error.message} variant="error" />
                </div>
            )}
            {tickets.isLoading ? (
                <p className="py-16 text-center text-muted-foreground">
                    티켓을 불러오고 있습니다...
                </p>
            ) : (
                <TicketList
                    games={games.data ?? []}
                    isCanceling={cancelTicketMutation.isPending}
                    isRetryingCancel={retryCancelTicketMutation.isPending}
                    onCancelTicket={async (ticketId) => {
                        await cancelTicketMutation.mutateAsync(ticketId);
                    }}
                    onRetryCancelTicket={async (ticketId) => {
                        await retryCancelTicketMutation.mutateAsync(ticketId);
                    }}
                    onReload={() => {
                        void tickets.refetch();
                    }}
                    reloading={tickets.isFetching}
                    tickets={tickets.data ?? []}
                />
            )}
        </main>
    );
}
