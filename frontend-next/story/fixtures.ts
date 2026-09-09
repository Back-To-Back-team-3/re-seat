import type {UserProfile} from "@/types/auth";
import type {StadiumCongestion} from "@/types/congestion";
import type {GameSeat, GameSummary, QueueViewState} from "@/types/game";
import type {OrderResponse} from "@/types/order";
import type {PaymentResponse} from "@/types/payment";
import type {ReservationResponse} from "@/types/reservation";
import type {TicketSummary} from "@/types/ticket";

export const storyProfile = {
    id: 1,
    email: "fan@example.com",
    name: "테스트 사용자",
    nickname: "야구팬",
    phone: "010-1234-5678",
    isVerified: true,
} satisfies UserProfile;

export const storyGames = [
    {
        gameId: 111,
        title: "LG 트윈스 vs 두산 베어스",
        homeTeam: {teamId: 1, name: "LG 트윈스"},
        awayTeam: {teamId: 2, name: "두산 베어스"},
        stadium: {stadiumId: 1, name: "잠실야구장"},
        gameAt: "2099-09-12 18:30:00",
        bookingOpenAt: "2099-09-01 10:00:00",
        bookingCloseAt: "2099-09-12 17:30:00",
        bookingStatus: "OPEN",
    },
    {
        gameId: 112,
        title: "키움 히어로즈 vs KIA 타이거즈",
        homeTeam: {teamId: 3, name: "키움 히어로즈"},
        awayTeam: {teamId: 4, name: "KIA 타이거즈"},
        stadium: {stadiumId: 2, name: "고척스카이돔"},
        gameAt: "2099-09-13 14:00:00",
        bookingOpenAt: "2099-09-02 10:00:00",
        bookingCloseAt: "2099-09-13 13:00:00",
        bookingStatus: "SCHEDULED",
    },
] satisfies GameSummary[];

export const storySeats = Array.from({length: 20}, (_, index) => ({
    gameSeatId: index + 1,
    zoneId: 1,
    zoneName: "1루 A",
    grade: "INFIELD" as const,
    seatBlock: "A",
    seatRow: index < 10 ? "A" : "B",
    seatNumber: String((index % 10) + 1),
    price: 18_000,
    status:
        index === 4
            ? ("HELD" as const)
            : index === 14
              ? ("SOLD" as const)
              : ("AVAILABLE" as const),
})) satisfies GameSeat[];

export const storyReservation = {
    reservationId: 201,
    reservationNo: "RSV-20990912-001",
    status: "HOLDING",
    gameSeats: storySeats.slice(0, 2).map((seat) => ({
        gameSeatId: seat.gameSeatId,
        status: "HELD" as const,
        price: seat.price,
    })),
    holdExpiresAt: "2099-09-12 18:10:00",
    gameAt: storyGames[0].gameAt,
} satisfies ReservationResponse;

export const storyOrder = {
    orderId: 301,
    orderNo: "ORD-20990912-001",
    totalAmount: 36_000,
    status: "CREATED",
    paymentDeadline: "2099-09-12 18:15:00",
    holdExpiresAt: storyReservation.holdExpiresAt,
    orderItems: storySeats.slice(0, 2).map((seat, index) => ({
        orderItemId: index + 1,
        gameSeatId: seat.gameSeatId,
        price: seat.price,
    })),
} satisfies OrderResponse;

export const storyPayment = {
    paymentId: 401,
    paymentNo: "PAY-20990912-001",
    orderId: storyOrder.orderId,
    amount: storyOrder.totalAmount,
    method: null,
    status: "READY",
    pgProvider: "TOSS",
    failReason: null,
    approvedAt: null,
    failedAt: null,
    canceledAmount: 0,
    remainingAmount: storyOrder.totalAmount,
    cancels: [],
} satisfies PaymentResponse;

export const storyQueue = {
    gameId: storyGames[0].gameId,
    rank: 128,
    estimatedWaitSeconds: 240,
    queueStatus: "WAITING",
    admitted: false,
    registrationPending: false,
    queueToken: null,
    tokenExpiresAt: null,
} satisfies QueueViewState;

export const storyTicket = {
    ticketId: 501,
    ticketNo: "TICKET-20990912-001",
    gameId: storyGames[0].gameId,
    seat: "1루 A A열 1번",
    status: "ISSUED",
    qrToken: "QR-20990912-001",
    gameAt: storyGames[0].gameAt,
    refundable: true,
    refundDeadline: "2099-09-11 18:30:00",
} satisfies TicketSummary;

export const storyCongestion = {
    stadiumNum: 1,
    stadiumName: "잠실야구장",
    areaName: "잠실종합운동장",
    congestionLevel: "약간 붐빔",
    congestionMessage: "경기 시작 전 관람객이 늘고 있습니다.",
    populationMin: 12_000,
    populationMax: 14_000,
    latitude: 37.5121,
    longitude: 127.0719,
    observedAt: "2099-09-12 17:30:00",
} satisfies StadiumCongestion;
