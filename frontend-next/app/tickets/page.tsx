"use client";

import Link from "next/link";

import {Alert} from "@/components/common/alert";
import {TicketList} from "@/components/tickets/ticket-list";
import {useAuth} from "@/hooks/use-auth";
import {useGames} from "@/hooks/use-games";
import {useTickets} from "@/hooks/use-tickets";

export default function TicketsPage() {
    const auth = useAuth();
    const tickets = useTickets(auth.isAuthed);
    const games = useGames();

    if (!auth.isAuthed) {
        return (
            <main className="grid min-h-[60vh] place-items-center p-6">
                <section className="text-center">
                    <h1 className="text-2xl font-bold">로그인이 필요합니다.</h1>
                    <Link className="mt-4 inline-block text-brand" href="/games">
                        경기 목록으로 돌아가기
                    </Link>
                </section>
            </main>
        );
    }

    // Vite의 티켓 화면은 activeStep이 "tickets"일 때 flow-header(뒤로 가기)를
    // 렌더링하지 않는다(App.tsx의 `activeStep !== "games" && activeStep !== "tickets"`
    // 조건). 이 페이지도 동일하게 뒤로 가기 링크 없이 화면을 구성한다.
    return (
        <main
            className="relative mx-auto min-h-[650px] w-full max-w-[var(--width-shell)] px-[var(--gutter-desktop)] pt-12 pb-[90px] max-sm:px-[var(--gutter-mobile)] max-sm:pt-[46px] max-sm:pb-[70px]">
            {tickets.error && (
                <Alert message={tickets.error.message} variant="error"/>
            )}
            {tickets.isLoading ? (
                <p className="py-16 text-center text-muted-foreground">
                    티켓을 불러오고 있습니다.
                </p>
            ) : (
                <TicketList
                    games={games.data ?? []}
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
