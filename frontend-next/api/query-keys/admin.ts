import type {AdminGameSearchCondition, AdminUserSearchCondition} from "@/types/admin";

export const adminKeys = {
    all: ["admin"] as const,
    games: () => [...adminKeys.all, "games"] as const,
    gameList: (condition: AdminGameSearchCondition, page: number, size: number) =>
        [...adminKeys.games(), "list", condition, page, size] as const,
    gameSeats: (gameId: number) =>
        [...adminKeys.games(), gameId, "seats"] as const,
    gameSeatSummary: (gameId: number) =>
        [...adminKeys.gameSeats(gameId), "summary"] as const,
    gameReservations: (gameId: number) =>
        [...adminKeys.games(), gameId, "reservations"] as const,
    gameQueue: (gameId: number) =>
        [...adminKeys.games(), gameId, "queue"] as const,
    users: () => [...adminKeys.all, "users"] as const,
    userList: (condition: AdminUserSearchCondition, page: number, size: number) =>
        [...adminKeys.users(), "list", condition, page, size] as const,
    user: (userId: number) => [...adminKeys.users(), "detail", userId] as const,
    userTickets: (userId: number) =>
        [...adminKeys.user(userId), "tickets"] as const,
};
