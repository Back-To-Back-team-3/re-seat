"use client";

import {useMutation, useQuery, useQueryClient} from "@tanstack/react-query";

import {ticketKeys} from "@/api/query-keys/tickets";
import {cancelTicket, getTickets} from "@/api/tickets";

import type {TicketSummary} from "@/types/ticket";

export function useTickets(enabled: boolean) {
    return useQuery({
        queryKey: ticketKeys.list(),
        enabled,
        queryFn: getTickets,
    });
}

export function useCancelTicket() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (ticketId: number) => cancelTicket(ticketId),
        onSuccess: (result) => {
            queryClient.setQueryData<TicketSummary[]>(ticketKeys.list(), (old) => {
                if (!old) return old;
                return old.map((ticket) =>
                    ticket.ticketId === result.ticketId
                        ? {
                              ...ticket,
                              status: result.ticketStatus,
                              refundable: false,
                          }
                        : ticket,
                );
            });
            void queryClient.invalidateQueries({queryKey: ticketKeys.list()});
        },
    });
}
