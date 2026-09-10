import {http, HttpResponse} from "msw";
import {describe, expect, it} from "vitest";

import {
    cancelAdminTicket,
    getAdminUser,
    getAdminUserTickets,
    openGameSeatInventory,
    searchAdminUsers,
    updateGameBookingStatus,
    updateUserRole,
} from "@/api/admin";
import {API_BASE_URL} from "@/api/client";
import {server} from "@/test/mocks/server";

describe("관리자 API", () => {
    it("회원 상세의 본인인증 필드를 프론트 표준 이름으로 정규화한다", async () => {
        server.use(
            http.get(`${API_BASE_URL}/admin/users/7`, () =>
                HttpResponse.json({
                    success: true,
                    errorCode: null,
                    message: "회원 상세 조회 완료",
                    data: {
                        id: 7,
                        email: "user@example.com",
                        name: "사용자",
                        nickname: "야구팬",
                        phone: "010-1234-5678",
                        role: "USER",
                        status: "ACTIVE",
                        verified: true,
                        createdAt: "2026-09-01T10:00:00",
                        updatedAt: "2026-09-01T10:00:00",
                    },
                }),
            ),
        );

        await expect(getAdminUser(7)).resolves.toMatchObject({
            id: 7,
            isVerified: true,
        });
    });

    it("회원 검색 조건과 페이지 조건을 쿼리로 전달한다", async () => {
        server.use(
            http.get(`${API_BASE_URL}/admin/users`, ({request}) => {
                const params = new URL(request.url).searchParams;
                expect(params.get("email")).toBe("admin@example.com");
                expect(params.get("role")).toBe("ADMIN");
                expect(params.get("page")).toBe("1");
                expect(params.get("size")).toBe("20");

                return HttpResponse.json({
                    success: true,
                    errorCode: null,
                    message: "회원 목록 조회 완료",
                    data: {
                        content: [],
                        pageNumber: 1,
                        pageSize: 20,
                        totalElements: 0,
                        totalPages: 0,
                        isFirst: false,
                        isLast: true,
                    },
                });
            }),
        );

        const result = await searchAdminUsers(
            {email: "admin@example.com", role: "ADMIN"},
            1,
            20,
        );

        expect(result.content).toEqual([]);
    });

    it("회원 권한과 경기 예매 상태를 각 변경 API에 전달한다", async () => {
        server.use(
            http.patch(`${API_BASE_URL}/admin/users/7/role`, async ({request}) => {
                expect(await request.json()).toEqual({role: "ADMIN"});
                return HttpResponse.json({success: true, errorCode: null, message: "회원 권한 변경 완료", data: null});
            }),
            http.patch(`${API_BASE_URL}/admin/games/111/booking-status`, async ({request}) => {
                expect(await request.json()).toEqual({bookingStatus: "OPEN", reason: "예매 시작"});
                return HttpResponse.json({
                    success: true,
                    errorCode: null,
                    message: "예매 상태 변경",
                    data: {gameId: 111, bookingStatus: "OPEN"},
                });
            }),
        );

        await updateUserRole(7, "ADMIN");
        await expect(
            updateGameBookingStatus(111, "OPEN", "예매 시작"),
        ).resolves.toEqual({gameId: 111, bookingStatus: "OPEN"});
    });

    it("좌석 재고 오픈 결과를 반환한다", async () => {
        server.use(
            http.post(`${API_BASE_URL}/admin/games/111/seats`, () =>
                HttpResponse.json({
                    success: true,
                    errorCode: null,
                    message: "좌석 재고 오픈 성공",
                    data: {
                        gameId: 111,
                        createdCount: 500,
                        priceRange: {min: 16000, max: 18000},
                    },
                }),
            ),
        );

        await expect(openGameSeatInventory(111)).resolves.toEqual({
            gameId: 111,
            createdCount: 500,
            priceRange: {min: 16000, max: 18000},
        });
    });

    it("사용자 티켓 조회와 관리자 취소 요청을 전달한다", async () => {
        server.use(
            http.get(`${API_BASE_URL}/admin/tickets/users/7`, ({request}) => {
                expect(new URL(request.url).searchParams.get("status")).toBe("ISSUED");
                return HttpResponse.json({
                    success: true,
                    errorCode: null,
                    message: "사용자 티켓 소유 목록 조회 완료",
                    data: {
                        content: [],
                        pageNumber: 0,
                        pageSize: 20,
                        totalElements: 0,
                        totalPages: 0,
                        isFirst: true,
                        isLast: true,
                    },
                });
            }),
            http.post(`${API_BASE_URL}/admin/tickets/30/cancel`, async ({request}) => {
                expect(await request.json()).toEqual({reason: "관리자 직권 취소"});
                return HttpResponse.json({
                    success: true,
                    errorCode: null,
                    message: "관리자 직권 티켓 강제 취소 요청 접수 완료",
                    data: {
                        ticketId: 30,
                        ticketNo: "TKT-30",
                        status: "REFUND_PENDING",
                        cancelReason: "ADMIN_FORCE_CANCEL",
                        cancelDetail: "관리자 직권 취소",
                        canceledAt: null,
                        gameSeatId: 40,
                        seatStatus: "SOLD",
                    },
                });
            }),
        );

        const tickets = await getAdminUserTickets(7, "ISSUED");
        expect(tickets.content).toEqual([]);
        await expect(
            cancelAdminTicket(30, "관리자 직권 취소"),
        ).resolves.toMatchObject({ticketId: 30, status: "REFUND_PENDING"});
    });
});
