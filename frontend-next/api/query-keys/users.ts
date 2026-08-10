/**
 * 사용자 서버 상태의 TanStack Query 캐시 키를 한곳에서 관리합니다.
 */
export const userKeys = {
    all: ["users"] as const,
    me: () => [...userKeys.all, "me"] as const,
};
