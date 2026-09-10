"use client";

import {useMutation, useQuery, useQueryClient} from "@tanstack/react-query";

import {
    cancelAdminTicket,
    getAdminUser,
    getAdminUserTickets,
    searchAdminUsers,
    updateUserRole,
    updateUserStatus,
} from "@/api/admin";
import {adminKeys} from "@/api/query-keys/admin";
import type {
    AdminUserSearchCondition,
    UserStatus,
} from "@/types/admin";
import type {UserRole} from "@/types/auth";

export function useAdminUsers(
    condition: AdminUserSearchCondition,
    page: number,
    size = 20,
) {
    return useQuery({
        queryKey: adminKeys.userList(condition, page, size),
        queryFn: () => searchAdminUsers(condition, page, size),
    });
}

export function useAdminUserDetail(userId: number | null) {
    const queryClient = useQueryClient();
    const enabled = userId !== null;

    const userQuery = useQuery({
        queryKey: enabled ? adminKeys.user(userId) : [...adminKeys.users(), "none"],
        queryFn: () => getAdminUser(userId as number),
        enabled,
    });
    const ticketQuery = useQuery({
        queryKey: enabled
            ? adminKeys.userTickets(userId)
            : [...adminKeys.users(), "none", "tickets"],
        queryFn: () => getAdminUserTickets(userId as number),
        enabled,
    });

    const roleMutation = useMutation({
        mutationFn: (role: UserRole) => updateUserRole(userId as number, role),
        onSuccess: async () => {
            await Promise.all([
                queryClient.invalidateQueries({queryKey: adminKeys.user(userId as number)}),
                queryClient.invalidateQueries({queryKey: adminKeys.users()}),
            ]);
        },
    });
    const statusMutation = useMutation({
        mutationFn: (status: UserStatus) =>
            updateUserStatus(userId as number, status),
        onSuccess: async () => {
            await Promise.all([
                queryClient.invalidateQueries({queryKey: adminKeys.user(userId as number)}),
                queryClient.invalidateQueries({queryKey: adminKeys.users()}),
            ]);
        },
    });
    const cancelMutation = useMutation({
        mutationFn: ({ticketId, reason}: {ticketId: number; reason: string}) =>
            cancelAdminTicket(ticketId, reason),
        onSuccess: async () => {
            await queryClient.invalidateQueries({
                queryKey: adminKeys.userTickets(userId as number),
            });
        },
    });

    return {
        user: userQuery.data ?? null,
        tickets: ticketQuery.data ?? null,
        isLoading: userQuery.isLoading || ticketQuery.isLoading,
        error: userQuery.error ?? ticketQuery.error ?? roleMutation.error ??
            statusMutation.error ?? cancelMutation.error,
        updateRole: (role: UserRole) => roleMutation.mutateAsync(role),
        updateStatus: (status: UserStatus) => statusMutation.mutateAsync(status),
        cancelTicket: (ticketId: number, reason: string) =>
            cancelMutation.mutateAsync({ticketId, reason}),
        isUpdatingRole: roleMutation.isPending,
        isUpdatingStatus: statusMutation.isPending,
        isCancelingTicket: cancelMutation.isPending,
    };
}
