"use client";

import {useQuery} from "@tanstack/react-query";

import {ticketKeys} from "@/api/query-keys/tickets";
import {getTickets} from "@/api/tickets";
import {getMockTickets} from "@/lib/mock-tickets";

export function useTickets(enabled: boolean) {
    return useQuery({
        queryKey: ticketKeys.list(),
        enabled,
        queryFn: async () => {
            const tickets = await getTickets();
            if (tickets.length > 0) {
                return {tickets, source: "api" as const};
            }

            return {tickets: getMockTickets(), source: "mock" as const};
        },
    });
}
