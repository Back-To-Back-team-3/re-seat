"use client";

import {useMutation, useQuery, useQueryClient} from "@tanstack/react-query";

import {ticketKeys} from "@/api/query-keys/tickets";
import {cancelTicket, getTickets} from "@/api/tickets";

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
        onSuccess: () => {
            void queryClient.invalidateQueries({queryKey: ticketKeys.list()});
        },
    });
}
