"use client";

import Link from "next/link";

import { Alert } from "@/components/common/alert";
import { TicketList } from "@/components/tickets/ticket-list";
import { useAuth } from "@/hooks/use-auth";
import { useTickets } from "@/hooks/use-tickets";

export default function TicketsPage() {
  const auth = useAuth();
  const tickets = useTickets(auth.isAuthed);

  if (!auth.isAuthed) {
    return (
      <main className="grid min-h-screen place-items-center p-6">
        <section className="text-center">
          <h1 className="text-2xl font-bold">로그인이 필요합니다.</h1>
          <Link className="mt-4 inline-block text-brand" href="/games">
            경기 목록으로 돌아가기
          </Link>
        </section>
      </main>
    );
  }

  return (
    <main className="mx-auto grid min-h-screen w-full max-w-5xl gap-8 px-[5vw] py-14">
      <div>
        <Link className="text-sm text-muted-foreground" href="/games">
          ← 경기 목록
        </Link>
        <h1 className="mt-4 text-3xl font-black">내 티켓</h1>
      </div>
      {tickets.error && (
        <Alert message={tickets.error.message} variant="error" />
      )}
      {tickets.isLoading ? (
        <p>티켓을 불러오고 있습니다.</p>
      ) : (
        <TicketList
          source={tickets.data?.source ?? "api"}
          tickets={tickets.data?.tickets ?? []}
        />
      )}
    </main>
  );
}
