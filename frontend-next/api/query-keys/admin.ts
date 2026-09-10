import type {AdminUserSearchCondition} from "@/types/admin";

export const adminKeys = {
    all: ["admin"] as const,
    users: () => [...adminKeys.all, "users"] as const,
    userList: (condition: AdminUserSearchCondition, page: number, size: number) =>
        [...adminKeys.users(), "list", condition, page, size] as const,
    user: (userId: number) => [...adminKeys.users(), "detail", userId] as const,
    userTickets: (userId: number) =>
        [...adminKeys.user(userId), "tickets"] as const,
};
