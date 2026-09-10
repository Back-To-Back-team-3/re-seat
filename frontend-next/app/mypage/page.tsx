"use client";

import Link from "next/link";
import {useState} from "react";

import {Alert} from "@/components/common/alert";
import {AccountManagement} from "@/components/mypage/account-management";
import {
    MyPageNavigation,
    type MyPageTab,
} from "@/components/mypage/mypage-navigation";
import {TicketList} from "@/components/tickets/ticket-list";
import {Button} from "@/components/ui/button";
import {useAuth} from "@/hooks/use-auth";
import {useGames} from "@/hooks/use-games";
import {useCancelTicket, useRetryCancelTicket, useTickets} from "@/hooks/use-tickets";

export default function MyPage() {
    const [activeTab, setActiveTab] = useState<MyPageTab>("tickets");
    const auth = useAuth();
    const tickets = useTickets(auth.isAuthed && activeTab === "tickets");
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
                    <Button
                        className="mt-6"
                        onClick={auth.login}
                        type="button"
                    >
                        로그인하기
                    </Button>
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

            <div className="mx-auto grid w-full max-w-[1120px] overflow-hidden rounded-modal border border-border bg-surface shadow-sm md:grid-cols-[220px_minmax(0,1fr)]">
                <MyPageNavigation
                    activeTab={activeTab}
                    onSelect={setActiveTab}
                />

                {activeTab === "tickets" ? (
                    <section
                        aria-labelledby="mypage-tickets-tab"
                        className="min-w-0 p-5 sm:p-8 md:p-10"
                        id="mypage-tickets-panel"
                        role="tabpanel"
                    >
                        {tickets.error && (
                            <div className="mb-4">
                                <Alert
                                    message={tickets.error.message}
                                    variant="error"
                                />
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
                    </section>
                ) : (
                    auth.profile ? (
                        <AccountManagement
                            isUpdating={auth.isUpdatingProfile}
                            isWithdrawing={auth.isWithdrawing}
                            key={`${auth.profile.id}-${auth.profile.name}-${auth.profile.phone}`}
                            onUpdate={auth.updateProfile}
                            onWithdraw={auth.withdraw}
                            profile={auth.profile}
                        />
                    ) : (
                        <p className="py-16 text-center text-muted-foreground">
                            회원정보를 불러오고 있습니다...
                        </p>
                    )
                )}
            </div>
        </main>
    );
}
