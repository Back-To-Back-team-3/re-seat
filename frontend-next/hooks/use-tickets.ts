"use client";

import {useQuery} from "@tanstack/react-query";

import {ticketKeys} from "@/api/query-keys/tickets";
import {getTickets} from "@/api/tickets";

export function useTickets(enabled: boolean) {
    return useQuery({
        queryKey: ticketKeys.list(),
        enabled,
        queryFn: getTickets,
    });
}
