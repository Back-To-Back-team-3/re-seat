"use client";

import {useMutation, useQuery, useQueryClient} from "@tanstack/react-query";

import {
    cancelAdminGameTickets,
    cancelAdminTicket,
    reissueAdminTicketQr,
    searchAdminTickets,
    verifyAdminTicket,
} from "@/api/admin";
import {adminKeys} from "@/api/query-keys/admin";
import {ticketKeys} from "@/api/query-keys/tickets";
import type {AdminTicketSearchCondition} from "@/types/admin";

export function useAdminTickets(
    condition: AdminTicketSearchCondition,
    page: number,
    size: number,
) {
    const queryClient = useQueryClient();
    const tickets = useQuery({
        queryKey: adminKeys.ticketList(condition, page, size),
        queryFn: () => searchAdminTickets(condition, page, size),
    });
    const refreshTickets = async () => {
        await Promise.all([
            queryClient.invalidateQueries({queryKey: adminKeys.tickets()}),
            queryClient.invalidateQueries({queryKey: adminKeys.users()}),
            queryClient.invalidateQueries({queryKey: ticketKeys.all}),
        ]);
    };
    const cancelMutation = useMutation({
        mutationFn: ({ticketId, reason}: {ticketId: number; reason: string}) =>
            cancelAdminTicket(ticketId, reason),
        onSuccess: refreshTickets,
    });
    const reissueMutation = useMutation({
        mutationFn: reissueAdminTicketQr,
        onSuccess: refreshTickets,
    });
    const verifyMutation = useMutation({
        mutationFn: ({gameId, qrToken}: {gameId: number; qrToken: string}) =>
            verifyAdminTicket(gameId, qrToken),
        onSuccess: refreshTickets,
    });
    const bulkCancelMutation = useMutation({
        mutationFn: ({gameId, reason}: {gameId: number; reason: string}) =>
            cancelAdminGameTickets(gameId, reason),
        onSuccess: refreshTickets,
    });

    return {
        tickets: tickets.data ?? null,
        isLoading: tickets.isLoading,
        error: tickets.error ?? cancelMutation.error ?? reissueMutation.error ??
            verifyMutation.error ?? bulkCancelMutation.error,
        cancelTicket: (ticketId: number, reason: string) =>
            cancelMutation.mutateAsync({ticketId, reason}),
        reissueQr: (ticketId: number) => reissueMutation.mutateAsync(ticketId),
        verifyTicket: (gameId: number, qrToken: string) =>
            verifyMutation.mutateAsync({gameId, qrToken}),
        cancelGameTickets: (gameId: number, reason: string) =>
            bulkCancelMutation.mutateAsync({gameId, reason}),
        isCanceling: cancelMutation.isPending,
        isReissuing: reissueMutation.isPending,
        isVerifying: verifyMutation.isPending,
        isBulkCanceling: bulkCancelMutation.isPending,
    };
}
