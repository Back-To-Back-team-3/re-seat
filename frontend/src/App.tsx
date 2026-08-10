import {useEffect, useMemo, useRef, useState} from "react";
import {
    cancelOrder,
    cancelQueue,
    cancelReservation,
    completePayment,
    createOrder,
    createReservation,
    enterQueue,
    failPayment,
    getGame,
    getGames,
    getGameSeats,
    getGameZones,
    getMyProfile,
    getOrder,
    getPayment,
    getQueueStatus,
    getReservationHoldTime,
    getTickets,
    requestPayment,
    streamQueue,
    verifyIdentity
} from "./api/services";
import {BACKEND_BASE_URL, clearTokens, getAccessTokenRole, setQueueToken, setTokens} from "./api/client";
import type {
    ApiResult,
    GameSeat,
    GameSummary,
    GameZone,
    OrderResponse,
    PaymentCreateResponse,
    QueueViewState,
    ReservationResponse,
    TicketSummary,
    UserProfile
} from "./types";

type Step = "games" | "queue" | "seats" | "checkout" | "payment" | "tickets";

const bookingSteps: Array<{ id: Step; label: string }> = [
    {id: "queue", label: "예매 대기"},
    {id: "seats", label: "좌석 선택"},
    {id: "checkout", label: "주문"},
    {id: "payment", label: "결제"}
];

const gameStatusMeta: Record<GameSummary["bookingStatus"], { label: string; action: string; description: string }> = {
    SCHEDULED: {label: "예매 예정", action: "예매 준비 중", description: "예매 오픈 전입니다."},
    OPEN: {label: "예매중", action: "경기 선택", description: "지금 예매할 수 있습니다."},
    CLOSED: {label: "예매 종료", action: "예매 종료", description: "예매가 마감되었습니다."},
    CANCELLED: {label: "경기 취소", action: "경기 취소", description: "취소된 경기입니다."}
};

const TOSS_CLIENT_KEY = import.meta.env.VITE_TOSS_CLIENT_KEY ?? "";
const PORTONE_CODE = import.meta.env.VITE_PORTONE_CODE ?? "imp31640540";
const PENDING_PAYMENT_KEY = "pendingTossPayment";
const PAYMENT_CALLBACK_KEY_PREFIX = "tossPaymentCallback:";
const PAYMENT_IDEMPOTENCY_KEY_PREFIX = "paymentIdempotencyKey:";
const COMPLETED_GAME_IDS_KEY = "completedGameIds";
const MOCK_TICKETS_KEY = "completedMockTickets";
const KST_TIME_ZONE = "Asia/Seoul";
const KST_OFFSET = "+09:00";
const STADIUM_IMAGE_URL = "/jamsil-stadium.jpg";

type PendingPayment = {
    orderId: number;
    gameId: number | null;
    payment: PaymentCreateResponse;
    idempotencyKey: string;
    seats?: GameSeat[];
};

function getInitialStep(): Step {
    const params = new URLSearchParams(window.location.search);
    return params.has("paymentId") && (params.has("paymentKey") || params.has("code"))
        ? "payment"
        : "games";
}

function formatPrice(value: number) {
    return `${new Intl.NumberFormat("ko-KR").format(value)}원`;
}

function parseApiDateTime(value?: string | null) {
    if (!value) return null;
    let normalized = value.trim().replace(" ", "T");
    normalized = normalized.replace(/\.(\d{3})\d+/, ".$1");
    if (!/(?:Z|[+-]\d{2}:?\d{2})$/i.test(normalized)) {
        // 백엔드와 DB가 Asia/Seoul을 사용하므로 오프셋 없는 LocalDateTime도 KST로 해석한다.
        normalized = normalized.includes("T")
            ? `${normalized}${KST_OFFSET}`
            : `${normalized}T00:00:00${KST_OFFSET}`;
    }
    const parsed = new Date(normalized);
    return Number.isNaN(parsed.getTime()) ? null : parsed;
}

function formatGameDate(value: string) {
    const date = parseApiDateTime(value);
    if (!date) return value;
    return new Intl.DateTimeFormat("ko-KR", {
        timeZone: KST_TIME_ZONE,
        month: "long",
        day: "numeric",
        weekday: "short",
        hour: "2-digit",
        minute: "2-digit",
        hour12: false
    }).format(date);
}

function formatShortDate(value: string) {
    const date = parseApiDateTime(value);
    if (!date) return {month: "--", day: "--", weekday: "---"};
    const parts = new Intl.DateTimeFormat("ko-KR", {
        timeZone: KST_TIME_ZONE,
        month: "numeric",
        day: "2-digit",
        weekday: "short"
    }).formatToParts(date);
    const part = (type: Intl.DateTimeFormatPartTypes) =>
        parts.find((entry) => entry.type === type)?.value ?? "";
    return {
        month: `${part("month")}월`,
        day: part("day"),
        weekday: part("weekday")
    };
}

function getCountdown(target?: string | null) {
    const targetDate = parseApiDateTime(target);
    if (!targetDate) return {label: "-", expired: false};
    const totalSeconds = Math.max(0, Math.ceil((targetDate.getTime() - Date.now()) / 1000));
    if (totalSeconds === 0) return {label: "만료", expired: true};
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return {label: `${minutes}:${seconds.toString().padStart(2, "0")}`, expired: false};
}

function Countdown({target, onExpire}: { target?: string | null; onExpire?: () => void }) {
    const [countdown, setCountdown] = useState(() => getCountdown(target));

    useEffect(() => {
        setCountdown(getCountdown(target));
        const timerId = window.setInterval(() => setCountdown(getCountdown(target)), 1000);
        return () => window.clearInterval(timerId);
    }, [target]);

    useEffect(() => {
        if (countdown.expired) onExpire?.();
    }, [countdown.expired, onExpire]);

    return <span className={countdown.expired ? "timer expired" : "timer"}>{countdown.label}</span>;
}

function isDeadlineExpired(target?: string | null) {
    return getCountdown(target).expired;
}

function toDateKey(value: string | Date) {
    const date = typeof value === "string" ? parseApiDateTime(value) : value;
    if (!date) return "";
    const parts = new Intl.DateTimeFormat("en-CA", {
        timeZone: KST_TIME_ZONE,
        year: "numeric",
        month: "2-digit",
        day: "2-digit"
    }).formatToParts(date);
    const part = (type: Intl.DateTimeFormatPartTypes) =>
        parts.find((entry) => entry.type === type)?.value ?? "";
    return [part("year"), part("month"), part("day")].join("-");
}

function createKstCalendarDate(year: number, month: number, day = 1) {
    return new Date(Date.UTC(year, month, day, 3));
}

function getCurrentKstCalendarMonth() {
    const [year, month] = toDateKey(new Date()).split("-").map(Number);
    return createKstCalendarDate(year, month - 1);
}

function getCompletedGameIds() {
    try {
        return new Set<number>(JSON.parse(localStorage.getItem(COMPLETED_GAME_IDS_KEY) ?? "[]"));
    } catch {
        return new Set<number>();
    }
}

function rememberCompletedGame(gameId?: number | null) {
    if (!gameId) return;
    const gameIds = getCompletedGameIds();
    gameIds.add(gameId);
    localStorage.setItem(COMPLETED_GAME_IDS_KEY, JSON.stringify([...gameIds]));
}

function getStoredMockTickets(): TicketSummary[] {
    try {
        const tickets = JSON.parse(sessionStorage.getItem(MOCK_TICKETS_KEY) ?? "[]");
        return Array.isArray(tickets) ? tickets as TicketSummary[] : [];
    } catch {
        return [];
    }
}

function createMockTickets(game: GameSummary, order: OrderResponse, seats: GameSeat[]): TicketSummary[] {
    return order.orderItems.map((orderItem, index) => {
        const seat = seats.find((candidate) => candidate.gameSeatId === orderItem.gameSeatId);
        return {
            ticketId: -orderItem.orderItemId,
            ticketNo: `MOCK-${order.orderNo}-${index + 1}`,
            gameId: game.gameId,
            seat: seat
                ? `${seat.zoneName} ${seat.seatRow}열 ${seat.seatNumber}번`
                : `좌석 #${orderItem.gameSeatId}`,
            status: "ISSUED" as const,
            qrToken: `MOCK-QR-${order.orderId}-${orderItem.orderItemId}`,
            gameAt: game.gameAt
        };
    });
}

function rememberMockTickets(tickets: TicketSummary[]) {
    const ticketsByNumber = new Map(
        [...getStoredMockTickets(), ...tickets].map((ticket) => [ticket.ticketNo, ticket])
    );
    const storedTickets = [...ticketsByNumber.values()].sort((left, right) =>
        right.gameAt.localeCompare(left.gameAt) || right.ticketId - left.ticketId
    );
    sessionStorage.setItem(MOCK_TICKETS_KEY, JSON.stringify(storedTickets));
    return storedTickets;
}

function EmptyState({title, description}: { title: string; description: string }) {
    return (
        <div className="empty-state">
            <span aria-hidden="true">○</span>
            <strong>{title}</strong>
            <p>{description}</p>
        </div>
    );
}

function BookingProgress({activeStep}: { activeStep: Step }) {
    const activeIndex = bookingSteps.findIndex((step) => step.id === activeStep);
    if (activeIndex < 0) return null;

    return (
        <div className="booking-progress" aria-label="예매 진행 단계">
            {bookingSteps.map((step, index) => (
                <div className={index < activeIndex ? "done" : index === activeIndex ? "active" : ""} key={step.id}>
                    <span>{index < activeIndex ? "✓" : index + 1}</span>
                    <strong>{step.label}</strong>
                </div>
            ))}
        </div>
    );
}

function App() {
    const [activeStep, setActiveStep] = useState<Step>(getInitialStep);
    const [theme, setTheme] = useState(localStorage.getItem("theme") ?? "light");
    const [isAuthed, setIsAuthed] = useState(Boolean(localStorage.getItem("accessToken")));
    const [profile, setProfile] = useState<UserProfile | null>(null);
    const [isVerified, setIsVerified] = useState(localStorage.getItem("isVerified") === "true");
    const [authedRole, setAuthedRole] = useState(isAuthed ? getAccessTokenRole() : "");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [toast, setToast] = useState<string | null>(null);

    const [gamesResult, setGamesResult] = useState<ApiResult<GameSummary[]> | null>(null);
    const [selectedGame, setSelectedGame] = useState<GameSummary | null>(null);
    const [queueResult, setQueueResult] = useState<ApiResult<QueueViewState> | null>(null);
    const [initialQueueRank, setInitialQueueRank] = useState<number | null>(null);
    const [zonesResult, setZonesResult] = useState<ApiResult<GameZone[]> | null>(null);
    const [selectedZoneId, setSelectedZoneId] = useState<number | null>(null);
    const [seatsResult, setSeatsResult] = useState<ApiResult<GameSeat[]> | null>(null);
    const [selectedSeatDetails, setSelectedSeatDetails] = useState<GameSeat[]>([]);
    const [reservationResult, setReservationResult] = useState<ApiResult<ReservationResponse> | null>(null);
    const [orderResult, setOrderResult] = useState<ApiResult<OrderResponse> | null>(null);
    const [paymentResult, setPaymentResult] = useState<ApiResult<PaymentCreateResponse> | null>(null);
    const [paymentIdempotencyKey, setPaymentIdempotencyKey] = useState<string | null>(null);
    const [ticketsResult, setTicketsResult] = useState<ApiResult<TicketSummary[]> | null>(null);
    const [exitIntent, setExitIntent] = useState<"home" | "tickets" | "logout" | null>(null);
    const [bookingNotice, setBookingNotice] = useState<{ title: string; description: string } | null>(null);

    const selectedSeats = selectedSeatDetails;
    const selectedSeatIds = selectedSeats.map((seat) => seat.gameSeatId);

    const seatRows = useMemo(() => {
        const rows = new Map<string, GameSeat[]>();
        (seatsResult?.data ?? []).forEach((seat) => {
            const row = rows.get(seat.seatRow) ?? [];
            row.push(seat);
            rows.set(seat.seatRow, row);
        });
        return Array.from(rows.entries());
    }, [seatsResult]);

    const totalAmount = selectedSeats.reduce((sum, seat) => sum + seat.price, 0);
    const currentQueueRank = queueResult?.data.rank ?? 0;
    const queueProgress = initialQueueRank && currentQueueRank
        ? Math.max(4, Math.min(100, ((initialQueueRank - currentQueueRank) / initialQueueRank) * 100))
        : queueResult?.data.admitted ? 100 : 4;

    useEffect(() => {
        void loadGames();
    }, []);

    useEffect(() => {
        const params = new URLSearchParams(window.location.search);
        const accessToken = params.get("accessToken");
        const refreshToken = params.get("refreshToken");
        const verifiedFromCallback = params.get("isVerified") === "true";

        if (accessToken && refreshToken) {
            window.history.replaceState({}, "", window.location.origin + window.location.pathname);
            setTokens(accessToken, refreshToken);
            localStorage.setItem("isVerified", String(verifiedFromCallback));
            setIsAuthed(true);
            setIsVerified(verifiedFromCallback);
            setAuthedRole(getAccessTokenRole());
            setToast("카카오 로그인에 성공했습니다.");
            void syncProfile();
            return;
        }

        if (isAuthed) void syncProfile();
    }, []);

    useEffect(() => {
        document.documentElement.dataset.theme = theme;
        localStorage.setItem("theme", theme);
    }, [theme]);

    useEffect(() => {
        if (activeStep === "games" || activeStep === "tickets") return;
        const warnBeforeUnload = (event: BeforeUnloadEvent) => {
            event.preventDefault();
            event.returnValue = "";
        };
        window.addEventListener("beforeunload", warnBeforeUnload);
        return () => window.removeEventListener("beforeunload", warnBeforeUnload);
    }, [activeStep]);

    useEffect(() => {
        if (!isAuthed || !selectedGame || activeStep !== "queue" || queueResult?.data.queueToken) return;

        const controller = new AbortController();
        streamQueue(selectedGame.gameId, {
            onRank: (status) => {
                if (status.rank > 0) setInitialQueueRank((current) => current ?? status.rank);
                setQueueResult((current) => ({
                    data: {
                        gameId: selectedGame.gameId,
                        ...status,
                        registrationPending: false,
                        queueToken: current?.data.queueToken ?? null,
                        tokenExpiresAt: current?.data.tokenExpiresAt ?? null
                    },
                    source: "api"
                }));
            },
            onAdmit: (event) => {
                setQueueResult({
                    data: {
                        gameId: selectedGame.gameId,
                        rank: 0,
                        estimatedWaitSeconds: 0,
                        queueStatus: "ADMITTED",
                        admitted: true,
                        registrationPending: false,
                        queueToken: event.queueToken,
                        tokenExpiresAt: event.tokenExpiresAt
                    },
                    source: "api"
                });
                setToast("대기열 입장이 완료되었습니다. 5분 안에 좌석을 선택해주세요.");
            }
        }, controller.signal).catch((streamError) => {
            if (streamError instanceof DOMException && streamError.name === "AbortError") return;
            setError(streamError instanceof Error ? streamError.message : "대기열 연결이 종료되었습니다.");
        });

        return () => controller.abort();
    }, [activeStep, isAuthed, queueResult?.data.queueToken, selectedGame]);

    useEffect(() => {
        const params = new URLSearchParams(window.location.search);
        const paymentId = Number(params.get("paymentId"));
        const paymentKey = params.get("paymentKey");
        const pgOrderId = params.get("orderId");
        const amount = Number(params.get("amount"));
        const code = params.get("code");
        const message = params.get("message");

        if (!paymentId || (!paymentKey && !code)) return;

        const callbackKey = `${PAYMENT_CALLBACK_KEY_PREFIX}${paymentId}:${paymentKey ?? code}`;
        if (sessionStorage.getItem(callbackKey)) {
            window.history.replaceState({}, "", window.location.pathname);
            return;
        }

        const pendingPayment = JSON.parse(
            sessionStorage.getItem(PENDING_PAYMENT_KEY) ?? "null"
        ) as PendingPayment | null;

        window.history.replaceState({}, "", window.location.pathname);
        if (!pendingPayment?.idempotencyKey) {
            setActiveStep("payment");
            setError("결제 콜백 세션을 찾을 수 없습니다. 주문 화면에서 결제 상태를 다시 확인해주세요.");
            return;
        }

        sessionStorage.setItem(callbackKey, "processing");
        void run(async () => {
            try {
                let approvedOrder: OrderResponse | null = null;
                if (paymentKey && pgOrderId && amount > 0) {
                    const action = await completePayment(
                        paymentId,
                        pendingPayment.idempotencyKey,
                        {paymentKey, orderId: pgOrderId, amount}
                    );
                    const [order, payment] = await Promise.all([
                        getOrder(pendingPayment.orderId),
                        getPayment(paymentId)
                    ]);
                    setOrderResult(order);
                    setPaymentResult({
                        data: {...pendingPayment.payment, ...payment, status: action.status},
                        source: "api"
                    });
                    if (action.status === "APPROVED") {
                        rememberCompletedGame(pendingPayment.gameId);
                        approvedOrder = order.data;
                    }
                    setToast(action.status === "APPROVED" ? "결제가 완료되었습니다." : "결제 결과를 확인했습니다.");
                } else if (code && message && pgOrderId) {
                    const action = await failPayment(
                        paymentId,
                        pendingPayment.idempotencyKey,
                        {code, message, orderId: pgOrderId}
                    );
                    setPaymentResult({
                        data: {...pendingPayment.payment, status: action.status},
                        source: "api"
                    });
                    setError(`결제 실패: ${message}`);
                }

                // 경기 조회와 임시 티켓 저장 실패가 결제 결과 처리를 막지 않도록 후속 작업으로 분리한다.
                try {
                    if (pendingPayment.gameId) {
                        const callbackGame = (await getGame(pendingPayment.gameId)).data;
                        setSelectedGame(callbackGame);
                        if (approvedOrder) {
                            rememberMockTickets(createMockTickets(
                                callbackGame,
                                approvedOrder,
                                pendingPayment.seats ?? []
                            ));
                        }
                    }
                } catch {
                    // 결제 결과 처리와 콜백 정리는 그대로 유지한다.
                }
                sessionStorage.setItem(callbackKey, "completed");
            } catch (callbackError) {
                sessionStorage.removeItem(callbackKey);
                throw callbackError;
            } finally {
                sessionStorage.removeItem(PENDING_PAYMENT_KEY);
                setActiveStep("payment");
            }
        });
    }, []);

    async function run<T>(action: () => Promise<T>, successMessage?: string) {
        setBusy(true);
        setError(null);
        try {
            const result = await action();
            if (successMessage) setToast(successMessage);
            return result;
        } catch (caught) {
            setError(caught instanceof Error ? caught.message : "요청 처리 중 문제가 발생했습니다.");
            return null;
        } finally {
            setBusy(false);
        }
    }

    async function syncProfile() {
        const result = await run(() => getMyProfile());
        if (!result) return;
        setProfile(result);
        setIsVerified(result.isVerified);
        localStorage.setItem("isVerified", String(result.isVerified));
        setAuthedRole(getAccessTokenRole());
    }

    async function loadGames() {
        const result = await run(() => getGames());
        if (!result) return;
        setGamesResult(result);
        setSelectedGame((current) => {
            if (current && result.data.some((game) => game.gameId === current.gameId)) return current;
            const todayKey = toDateKey(new Date());
            return result.data.find((game) =>
                    toDateKey(game.gameAt) === todayKey && game.bookingStatus === "OPEN"
                ) ?? result.data.find((game) => toDateKey(game.gameAt) === todayKey)
                ?? result.data.find((game) => game.bookingStatus === "OPEN")
                ?? result.data[0]
                ?? null;
        });
    }

    function resetBookingState() {
        setQueueToken(null);
        setQueueResult(null);
        setInitialQueueRank(null);
        setZonesResult(null);
        setSelectedZoneId(null);
        setSeatsResult(null);
        setSelectedSeatDetails([]);
        setReservationResult(null);
        setOrderResult(null);
        setPaymentResult(null);
        setPaymentIdempotencyKey(null);
    }

    async function handleSelectGame(game: GameSummary) {
        const result = await run(() => getGame(game.gameId));
        setSelectedGame(result?.data ?? game);
    }

    async function handleEnterQueue() {
        if (!selectedGame) return;
        if (selectedGame.bookingStatus !== "OPEN") {
            const statusMessage = selectedGame.bookingStatus === "SCHEDULED"
                ? "아직 예매가 시작되지 않은 경기입니다."
                : selectedGame.bookingStatus === "CLOSED"
                    ? "예매가 종료된 경기입니다."
                    : "취소된 경기는 예매할 수 없습니다.";
            setBookingNotice({title: gameStatusMeta[selectedGame.bookingStatus].label, description: statusMessage});
            return;
        }
        if (getCompletedGameIds().has(selectedGame.gameId)) {
            setBookingNotice({
                title: "이미 예매한 경기입니다.",
                description: "현재는 같은 경기의 추가 예매를 지원하지 않습니다. 추가 좌석 정책이 준비된 뒤 다시 이용해주세요."
            });
            return;
        }
        resetBookingState();
        const message = await run(() => enterQueue(selectedGame.gameId));
        if (!message) return;

        setQueueResult({
            data: {
                gameId: selectedGame.gameId,
                rank: 0,
                estimatedWaitSeconds: null,
                queueStatus: "WAITING",
                admitted: false,
                registrationPending: true,
                queueToken: null,
                tokenExpiresAt: null
            },
            source: "api"
        });
        setToast(message);
        setActiveStep("queue");
    }

    async function handleRefreshQueue() {
        if (!selectedGame) return;
        const status = await run(() => getQueueStatus(selectedGame.gameId));
        if (!status) return;
        if (status.rank > 0) setInitialQueueRank((current) => current ?? status.rank);
        setQueueResult((current) => ({
            data: {
                gameId: selectedGame.gameId,
                ...status,
                registrationPending: false,
                queueToken: current?.data.queueToken ?? null,
                tokenExpiresAt: current?.data.tokenExpiresAt ?? null
            },
            source: "api"
        }));
    }

    async function handleCancelQueue() {
        if (!selectedGame) return;
        const result = await run(() => cancelQueue(selectedGame.gameId), "대기열 진입을 취소했습니다.");
        if (!result) return;
        resetBookingState();
        setActiveStep("games");
    }

    async function handleLoadSeats() {
        if (!selectedGame || !queueResult?.data.queueToken) {
            setError("입장 토큰이 발급된 후 좌석을 조회할 수 있습니다.");
            return;
        }
        if (isDeadlineExpired(queueResult.data.tokenExpiresAt)) {
            setError("입장 토큰이 만료되었습니다. 예매를 다시 시작해주세요.");
            return;
        }

        const result = await run(async () => {
            const zones = await getGameZones(selectedGame.gameId);
            const zoneId = zones.data[0]?.zoneId;
            const seats = zoneId
                ? await getGameSeats(selectedGame.gameId, zoneId)
                : {data: [], source: "api" as const};
            return {zones, seats, zoneId: zoneId ?? null};
        });

        if (!result) return;
        setZonesResult(result.zones);
        setSelectedZoneId(result.zoneId);
        setSeatsResult(result.seats);
        setSelectedSeatDetails([]);
        setActiveStep("seats");
    }

    async function handleSelectZone(zoneId: number) {
        if (!selectedGame || zoneId === selectedZoneId || reservationResult) return;
        const result = await run(() => getGameSeats(selectedGame.gameId, zoneId));
        if (!result) return;
        setSelectedZoneId(zoneId);
        setSeatsResult(result);
    }

    function toggleSeat(seat: GameSeat) {
        if (seat.status !== "AVAILABLE" || reservationResult) return;
        if (selectedSeatIds.includes(seat.gameSeatId)) {
            setSelectedSeatDetails(selectedSeats.filter((selected) => selected.gameSeatId !== seat.gameSeatId));
            return;
        }
        if (selectedSeats.length >= 2) {
            setError("좌석은 최대 2개까지 선택할 수 있습니다.");
            return;
        }
        setSelectedSeatDetails([...selectedSeats, seat]);
    }

    async function handleReserveSeats() {
        if (!selectedGame || selectedSeatIds.length === 0) return;
        if (!queueResult?.data.queueToken || isDeadlineExpired(queueResult.data.tokenExpiresAt)) {
            setError("좌석 선택 가능 시간이 만료되었습니다. 예매를 다시 시작해주세요.");
            return;
        }
        const result = await run(
            () => createReservation(selectedGame.gameId, selectedSeatIds),
            "좌석 선점이 완료되었습니다."
        );
        if (!result) return;

        setReservationResult(result);
        setQueueToken(null);
        setQueueResult((current) => current ? {
            ...current,
            data: {...current.data, queueToken: null}
        } : current);

        const holdTime = await run(() => getReservationHoldTime(result.data.reservationId));
        if (holdTime) {
            setReservationResult({
                ...result,
                data: {...result.data, holdExpiresAt: holdTime.expiresAt, status: holdTime.status}
            });
        }
    }

    async function handleCancelReservation() {
        if (!reservationResult) return;
        const result = await run(
            () => cancelReservation(reservationResult.data.reservationId),
            "좌석 선점을 해제했습니다."
        );
        if (!result) return;

        setReservationResult(null);
        setSelectedSeatDetails([]);
        setOrderResult(null);
        setPaymentResult(null);
        setSeatsResult((current) => current ? {
            ...current,
            data: current.data.map((seat) => selectedSeatIds.includes(seat.gameSeatId)
                ? {...seat, status: "AVAILABLE" as const}
                : seat)
        } : current);
        setToast("좌석 선점을 해제했습니다. 현재 입장 토큰은 사용되어 새 선점은 다시 예매해야 합니다.");
    }

    async function handleCreateOrder() {
        if (!reservationResult) return;
        if (isDeadlineExpired(reservationResult.data.holdExpiresAt)) {
            setError("좌석 선점 시간이 만료되어 주문을 생성할 수 없습니다.");
            return;
        }
        const result = await run(
            () => createOrder(reservationResult.data.reservationId, "MOBILE"),
            "주문이 생성되었습니다."
        );
        if (result) setOrderResult(result);
    }

    async function handleRefreshOrder() {
        if (!orderResult) return;
        const result = await run(() => getOrder(orderResult.data.orderId), "주문 상태를 확인했습니다.");
        if (result) {
            setOrderResult(result);
            if (result.data.status === "PAID") rememberCompletedGame(selectedGame?.gameId);
        }
    }

    async function handleCancelOrder() {
        if (!orderResult) return;
        const result = await run(() => cancelOrder(orderResult.data.orderId), "주문을 취소했습니다.");
        if (!result) return;
        setOrderResult({...orderResult, data: {...orderResult.data, status: result.status}});
    }

    async function handlePayment() {
        if (!orderResult) return;
        if (paymentResult) {
            setActiveStep("payment");
            return;
        }
        if (isDeadlineExpired(orderResult.data.paymentDeadline)) {
            setError("결제 기한이 만료되어 결제를 준비할 수 없습니다.");
            return;
        }
        const orderId = orderResult.data.orderId;
        const storageKey = `${PAYMENT_IDEMPOTENCY_KEY_PREFIX}${orderId}`;
        const idempotencyKey = sessionStorage.getItem(storageKey)
            ?? (typeof crypto.randomUUID === "function"
                ? crypto.randomUUID()
                : `${orderId}-${Date.now()}-${Math.random().toString(36).slice(2)}`);
        sessionStorage.setItem(storageKey, idempotencyKey);

        const result = await run(
            () => requestPayment(orderId, idempotencyKey),
            "결제 요청이 준비되었습니다."
        );
        if (!result) return;
        setPaymentResult(result);
        if (result.data.status === "APPROVED") rememberCompletedGame(selectedGame?.gameId);
        setPaymentIdempotencyKey(idempotencyKey);
        setActiveStep("payment");
    }

    async function handleOpenTossPayment() {
        if (!paymentResult || !orderResult || !paymentIdempotencyKey) return;
        if (isDeadlineExpired(orderResult.data.paymentDeadline)) {
            setError("결제 기한이 만료되었습니다. 주문 상태를 확인해주세요.");
            return;
        }
        if (!TOSS_CLIENT_KEY) {
            setError("frontend/.env에 VITE_TOSS_CLIENT_KEY를 설정해주세요.");
            return;
        }
        if (!window.TossPayments) {
            setError("Toss SDK를 불러오지 못했습니다. 네트워크 상태를 확인해주세요.");
            return;
        }

        const payment = paymentResult.data;
        const baseUrl = window.location.origin + window.location.pathname;
        sessionStorage.setItem(PENDING_PAYMENT_KEY, JSON.stringify({
            orderId: orderResult.data.orderId,
            gameId: selectedGame?.gameId ?? null,
            payment,
            idempotencyKey: paymentIdempotencyKey,
            seats: selectedSeatDetails
        } satisfies PendingPayment));

        const tossPayments = window.TossPayments(TOSS_CLIENT_KEY);
        await run(() => tossPayments.requestPayment("카드", {
            amount: payment.amount,
            orderId: payment.pgOrderId,
            orderName: selectedGame?.title ?? "Re:Seat 티켓",
            customerName: profile?.name || profile?.nickname || "Re:Seat 사용자",
            customerEmail: profile?.email,
            successUrl: `${baseUrl}?paymentId=${payment.paymentId}`,
            failUrl: `${baseUrl}?paymentId=${payment.paymentId}`
        }));
    }

    async function handleLoadTickets() {
        const result = await run(async () => {
            const apiResult = await getTickets();
            if (apiResult.data.length > 0) return apiResult;

            let mockTickets = getStoredMockTickets();
            if (mockTickets.length === 0 && selectedGame && orderResult?.data.status === "PAID") {
                let seats = selectedSeatDetails;
                if (seats.length === 0) {
                    try {
                        seats = (await getGameSeats(selectedGame.gameId)).data;
                    } catch {
                        // 좌석 재조회가 실패해도 주문의 gameSeatId로 임시 티켓을 표시한다.
                    }
                }
                mockTickets = rememberMockTickets(createMockTickets(selectedGame, orderResult.data, seats));
            }

            return mockTickets.length > 0
                ? {
                    data: mockTickets,
                    source: "mock" as const,
                    message: "결제 완료 데이터를 바탕으로 만든 임시 티켓입니다."
                }
                : apiResult;
        });
        if (!result) return;
        setTicketsResult(result);
        setActiveStep("tickets");
    }

    function performLogout() {
        clearTokens();
        localStorage.removeItem("isVerified");
        sessionStorage.removeItem(MOCK_TICKETS_KEY);
        setIsAuthed(false);
        setProfile(null);
        setIsVerified(false);
        setAuthedRole("");
        resetBookingState();
        setTicketsResult(null);
        setActiveStep("games");
        setToast("로그아웃했습니다.");
    }

    function goHome() {
        if (activeStep !== "games" && activeStep !== "tickets") {
            setExitIntent("home");
            return;
        }
        setActiveStep("games");
        setError(null);
    }

    function openTickets() {
        if (activeStep !== "games" && activeStep !== "tickets") {
            setExitIntent("tickets");
            return;
        }
        void handleLoadTickets();
    }

    function logout() {
        if (activeStep !== "games" && activeStep !== "tickets") {
            setExitIntent("logout");
            return;
        }
        performLogout();
    }

    function confirmExit() {
        const intent = exitIntent;
        setExitIntent(null);
        resetBookingState();
        if (intent === "logout") {
            performLogout();
            return;
        }
        if (intent === "tickets") {
            void handleLoadTickets();
            return;
        }
        setActiveStep("games");
        setError(null);
    }

    const login = () => {
        window.location.href = `${BACKEND_BASE_URL}/oauth2/authorization/kakao`;
    };

    return (
        <div className="app-shell">
            <header className="topbar">
                <button className="brand" onClick={goHome} aria-label="Re:Seat 홈">
                    Re:<span>Seat</span>
                </button>
                <nav className="main-nav" aria-label="주요 메뉴">
                    <button className={activeStep === "games" ? "active" : ""} onClick={goHome}>경기 예매</button>
                    <button className={activeStep === "tickets" ? "active" : ""} onClick={openTickets}
                            disabled={!isAuthed}>내 티켓
                    </button>
                </nav>
                <div className="topbar-actions">
                    <button
                        className="icon-button"
                        onClick={() => setTheme((current) => current === "dark" ? "light" : "dark")}
                        aria-label="화면 테마 변경"
                    >
                        {theme === "dark" ? "☀" : "☾"}
                    </button>
                    {isAuthed ? (
                        <div className="profile-menu">
                            <span>{(profile?.nickname || profile?.name || profile?.email || "U").slice(0, 1)}</span>
                            <div>
                                <strong>{profile?.nickname || profile?.name || "회원"}</strong>
                                <small>{authedRole}</small>
                            </div>
                            <button onClick={logout}>로그아웃</button>
                        </div>
                    ) : (
                        <button className="kakao-button" onClick={login}>카카오 로그인</button>
                    )}
                </div>
            </header>

            {isAuthed && !isVerified ? (
                <main className="verification-page">
                    <Alert error={error} toast={toast} onClose={() => {
                        setError(null);
                        setToast(null);
                    }}/>
                    <section className="verification-card">
                        <div className="verification-visual">
                            <span className="eyebrow">IDENTITY CHECK</span>
                            <div className="verification-symbol" aria-hidden="true">✓</div>
                            <h1>안전한 예매를 위한<br/>마지막 한 단계</h1>
                            <p>부정 예매를 막고 공정한 예매 기회를 제공하기 위해 최초 한 번만 본인 확인을 진행합니다.</p>
                            <ol className="verification-steps">
                                <li className="done"><span>1</span>
                                    <div><strong>카카오 로그인</strong><small>계정 확인 완료</small></div>
                                </li>
                                <li className="active"><span>2</span>
                                    <div><strong>휴대폰 본인인증</strong><small>현재 단계</small></div>
                                </li>
                                <li><span>3</span>
                                    <div><strong>경기 예매 시작</strong><small>인증 후 바로 이용</small></div>
                                </li>
                            </ol>
                        </div>
                        <div className="verification-action">
                            <span className="verification-tag">최초 1회</span>
                            <h2>휴대폰 본인인증</h2>
                            <p>본인 명의의 휴대폰으로 인증하면 Re:Seat의 모든 예매 기능을 이용할 수 있습니다.</p>
                            <div className="verification-note"><span>⌕</span>
                                <div><strong>인증 정보는 안전하게 처리됩니다.</strong><small>인증 결과는 중복 예매 방지와 회원 식별에만 사용됩니다.</small>
                                </div>
                            </div>
                            <button className="primary-button full" disabled={busy}
                                    onClick={() => startIdentityVerification()}>
                                {busy ? "인증 요청 처리 중..." : "본인인증 시작하기"}
                            </button>
                            <button className="text-button" onClick={logout}>로그아웃 후 다른 계정으로 로그인</button>
                        </div>
                    </section>
                </main>
            ) : (
                <>
                    {activeStep !== "games" && activeStep !== "tickets" && (
                        <div className="flow-header">
                            <button onClick={goHome}>홈</button>
                            <span>›</span>
                            <strong>{bookingSteps.find((step) => step.id === activeStep)?.label}</strong>
                            <BookingProgress activeStep={activeStep}/>
                        </div>
                    )}

                    <main className={activeStep === "games" ? "home-page" : "page-content"}>
                        <Alert error={error} toast={toast} onClose={() => {
                            setError(null);
                            setToast(null);
                        }}/>

                        {activeStep === "games" && (
                            <HomeScreen
                                gamesResult={gamesResult}
                                selectedGame={selectedGame}
                                busy={busy}
                                isAuthed={isAuthed}
                                onLogin={login}
                                onReload={loadGames}
                                onSelectGame={handleSelectGame}
                                onEnterQueue={handleEnterQueue}
                            />
                        )}

                        {activeStep === "queue" && selectedGame && (
                            <QueueScreen
                                game={selectedGame}
                                queue={queueResult?.data ?? null}
                                progress={queueProgress}
                                busy={busy}
                                onRefresh={handleRefreshQueue}
                                onCancel={handleCancelQueue}
                                onContinue={handleLoadSeats}
                            />
                        )}

                        {activeStep === "seats" && selectedGame && (
                            <SeatScreen
                                game={selectedGame}
                                zonesResult={zonesResult}
                                seatsResult={seatsResult}
                                selectedZoneId={selectedZoneId}
                                selectedSeatIds={selectedSeatIds}
                                selectedSeats={selectedSeats}
                                seatRows={seatRows}
                                reservation={reservationResult?.data ?? null}
                                order={orderResult?.data ?? null}
                                queueTokenExpiresAt={queueResult?.data.tokenExpiresAt ?? null}
                                queueTokenAvailable={Boolean(queueResult?.data.queueToken)}
                                totalAmount={totalAmount}
                                busy={busy}
                                onSelectZone={handleSelectZone}
                                onToggleSeat={toggleSeat}
                                onReserve={handleReserveSeats}
                                onCancelReservation={handleCancelReservation}
                                onContinue={() => setActiveStep("checkout")}
                            />
                        )}

                        {activeStep === "checkout" && selectedGame && (
                            <CheckoutScreen
                                game={selectedGame}
                                seats={selectedSeats}
                                reservation={reservationResult?.data ?? null}
                                order={orderResult?.data ?? null}
                                paymentPrepared={Boolean(paymentResult)}
                                busy={busy}
                                onCreateOrder={handleCreateOrder}
                                onRefreshOrder={handleRefreshOrder}
                                onCancelOrder={handleCancelOrder}
                                onPayment={handlePayment}
                                onBack={() => setActiveStep("seats")}
                            />
                        )}

                        {activeStep === "payment" && (
                            <PaymentScreen
                                game={selectedGame}
                                order={orderResult?.data ?? null}
                                payment={paymentResult?.data ?? null}
                                busy={busy}
                                onOpenPayment={handleOpenTossPayment}
                                onRefreshOrder={handleRefreshOrder}
                                onTickets={handleLoadTickets}
                                onBack={() => setActiveStep("checkout")}
                            />
                        )}

                        {activeStep === "tickets" && (
                            <TicketScreen
                                result={ticketsResult}
                                games={gamesResult?.data ?? []}
                                busy={busy}
                                onReload={handleLoadTickets}
                            />
                        )}
                    </main>
                </>
            )}

            {exitIntent && (
                <ConfirmDialog
                    title="진행 중인 예매를 종료할까요?"
                    description="현재 화면의 선택 정보는 사라지며, 서버의 예약·주문은 제한시간이 끝날 때까지 유지될 수 있습니다."
                    confirmLabel="종료하고 이동"
                    onCancel={() => setExitIntent(null)}
                    onConfirm={confirmExit}
                />
            )}
            {bookingNotice && (
                <ConfirmDialog
                    title={bookingNotice.title}
                    description={bookingNotice.description}
                    confirmLabel="확인"
                    cancelLabel={null}
                    onCancel={() => setBookingNotice(null)}
                    onConfirm={() => setBookingNotice(null)}
                />
            )}

            <footer className="footer">
                <div><strong>Re:<span>Seat</span></strong><p>KBO 리그 공식 예매 파트너.<br/>최고의 경험을 최고의 자리에서.</p></div>
                <div><span>SUPPORT</span><small>이용 안내</small><small>고객 센터</small></div>
                <div><span>POLICY</span><small>이용 약관</small><small>개인정보 처리방침</small></div>
                <p>© 2026 Re:Seat. All rights reserved. · <a
                    href="https://commons.wikimedia.org/wiki/File:Doosan_Bears_vs_LG_Twins_(1).jpg" target="_blank"
                    rel="noreferrer">구장 사진: Christophe95, CC BY-SA 4.0</a></p>
            </footer>
        </div>
    );

    function startIdentityVerification() {
        const {IMP} = window as typeof window & { IMP?: any };
        if (!IMP) {
            setError("포트원 SDK를 불러오지 못했습니다.");
            return;
        }

        IMP.init(PORTONE_CODE);
        IMP.certification({
            pg: "inicis",
            merchant_uid: `verification_${Date.now()}`,
            popup: true
        }, async (response: { success: boolean; imp_uid: string; error_msg?: string }) => {
            if (!response.success) {
                setError(`본인인증 실패: ${response.error_msg ?? "인증을 완료하지 못했습니다."}`);
                return;
            }

            const verified = await run(async () => {
                await verifyIdentity(response.imp_uid);
                return getMyProfile();
            }, "본인인증이 완료되었습니다.");

            if (verified) {
                setProfile(verified);
                setIsVerified(verified.isVerified);
                localStorage.setItem("isVerified", String(verified.isVerified));
            }
        });
    }
}

function Alert({error, toast, onClose}: { error: string | null; toast: string | null; onClose: () => void }) {
    if (!error && !toast) return null;
    return (
        <div className={error ? "alert error" : "alert success"} role="status">
            <span>{error ? "!" : "✓"}</span>
            <p>{error ?? toast}</p>
            <button onClick={onClose} aria-label="알림 닫기">×</button>
        </div>
    );
}

function ConfirmDialog({title, description, confirmLabel, cancelLabel = "계속 예매하기", onCancel, onConfirm}: {
    title: string;
    description: string;
    confirmLabel: string;
    cancelLabel?: string | null;
    onCancel: () => void;
    onConfirm: () => void;
}) {
    const cancelButtonRef = useRef<HTMLButtonElement>(null);
    const confirmButtonRef = useRef<HTMLButtonElement>(null);

    useEffect(() => {
        if (cancelLabel) {
            cancelButtonRef.current?.focus();
        } else {
            confirmButtonRef.current?.focus();
        }

        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === "Escape") onCancel();
        };

        window.addEventListener("keydown", handleKeyDown);
        return () => window.removeEventListener("keydown", handleKeyDown);
    }, [cancelLabel, onCancel]);

    return (
        <div className="dialog-backdrop" role="presentation" onMouseDown={onCancel}>
            <section className="confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="exit-dialog-title"
                     onMouseDown={(event) => event.stopPropagation()}>
                <span className="dialog-symbol" aria-hidden="true">!</span>
                <h2 id="exit-dialog-title">{title}</h2>
                <p>{description}</p>
                <div className={cancelLabel ? "dialog-actions" : "dialog-actions single"}>
                    {cancelLabel && <button ref={cancelButtonRef} className="outline-button"
					                        onClick={onCancel}>{cancelLabel}</button>}
                    <button ref={confirmButtonRef} className="primary-button"
                            onClick={onConfirm}>{confirmLabel}</button>
                </div>
            </section>
        </div>
    );
}

function HomeScreen({
                        gamesResult,
                        selectedGame,
                        busy,
                        isAuthed,
                        onLogin,
                        onReload,
                        onSelectGame,
                        onEnterQueue
                    }: {
    gamesResult: ApiResult<GameSummary[]> | null;
    selectedGame: GameSummary | null;
    busy: boolean;
    isAuthed: boolean;
    onLogin: () => void;
    onReload: () => void;
    onSelectGame: (game: GameSummary) => void;
    onEnterQueue: () => void;
}) {
    const games = gamesResult?.data ?? [];
    const [calendarCursor, setCalendarCursor] = useState(getCurrentKstCalendarMonth);
    const [selectedDate, setSelectedDate] = useState<string | null>(() => toDateKey(new Date()));
    const [teamFilter, setTeamFilter] = useState("ALL");
    const [stadiumFilter, setStadiumFilter] = useState("ALL");
    const [statusFilter, setStatusFilter] = useState("ALL");

    const teams = useMemo(() => {
        const entries = new Map<number, string>();
        games.forEach((game) => {
            entries.set(game.homeTeam.teamId, game.homeTeam.name);
            entries.set(game.awayTeam.teamId, game.awayTeam.name);
        });
        return [...entries.entries()].sort((a, b) => a[1].localeCompare(b[1], "ko"));
    }, [games]);

    const stadiums = useMemo(() => {
        const entries = new Map<number, string>();
        games.forEach((game) => entries.set(game.stadium.stadiumId, game.stadium.name));
        return [...entries.entries()].sort((a, b) => a[1].localeCompare(b[1], "ko"));
    }, [games]);

    const filterGame = (game: GameSummary, includeDate = true) => {
        const teamId = Number(teamFilter);
        const stadiumId = Number(stadiumFilter);
        if (includeDate && selectedDate && toDateKey(game.gameAt) !== selectedDate) return false;
        if (teamFilter !== "ALL" && game.homeTeam.teamId !== teamId && game.awayTeam.teamId !== teamId) return false;
        if (stadiumFilter !== "ALL" && game.stadium.stadiumId !== stadiumId) return false;
        if (statusFilter !== "ALL" && game.bookingStatus !== statusFilter) return false;
        return true;
    };

    const filteredGames = games.filter((game) => filterGame(game));
    const calendarFilteredGames = games.filter((game) => filterGame(game, false));
    const todayKey = toDateKey(new Date());
    const todayGames = games.filter((game) => toDateKey(game.gameAt) === todayKey);
    const year = calendarCursor.getUTCFullYear();
    const month = calendarCursor.getUTCMonth();
    const firstWeekday = new Date(Date.UTC(year, month, 1)).getUTCDay();
    const lastDate = new Date(Date.UTC(year, month + 1, 0)).getUTCDate();
    const calendarCells: Array<number | null> = [
        ...Array.from({length: firstWeekday}, () => null),
        ...Array.from({length: lastDate}, (_, index) => index + 1)
    ];
    const completedGameIds = getCompletedGameIds();
    const selectedMeta = selectedGame ? gameStatusMeta[selectedGame.bookingStatus] : null;
    const selectedCompleted = selectedGame ? completedGameIds.has(selectedGame.gameId) : false;

    const moveMonth = (offset: number) => {
        setCalendarCursor(createKstCalendarDate(year, month + offset));
        setSelectedDate(null);
    };

    const selectCalendarDay = (day: number) => {
        const dateKey = toDateKey(createKstCalendarDate(year, month, day));
        setSelectedDate((current) => current === dateKey ? null : dateKey);
    };

    const showToday = () => {
        setCalendarCursor(getCurrentKstCalendarMonth());
        setSelectedDate(todayKey);
    };

    return (
        <>
            <section className="hero-banner">
                <div className="hero-copy">
                    <span className="eyebrow">2026 KBO LEAGUE</span>
                    <h1>지금 바로<br/><em>예매하세요</em></h1>
                    <p>KBO 리그 전 구단 홈 경기를 확인하고<br/>공정한 대기열을 통해 원하는 좌석을 선택하세요.</p>
                    <div className="hero-stats">
                        <div><strong>10개</strong><span>구단</span></div>
                        <div><strong>500</strong><span>데모 좌석/경기</span></div>
                        <div><strong>2석</strong><span>최대 선택</span></div>
                    </div>
                    {selectedGame && (
                        <div className="hero-selected-game">
                            <div><span>SELECTED GAME · {selectedMeta?.label}</span><strong>{selectedGame.title}</strong><small>{formatGameDate(selectedGame.gameAt)} · {selectedGame.stadium.name}</small>
                            </div>
                            <button className="primary-button" onClick={isAuthed ? onEnterQueue : onLogin}
                                    disabled={busy || selectedGame.bookingStatus !== "OPEN" || (isAuthed && selectedCompleted)}>
                                {selectedCompleted ? "예매 완료" : isAuthed ? selectedMeta?.action : "로그인 후 예매"}<span>→</span>
                            </button>
                        </div>
                    )}
                </div>
                <div className="hero-side">
                    <section className="today-panel">
                        <div className="today-panel-head">
                            <span>— 오늘의 경기</span><strong>{new Intl.DateTimeFormat("ko-KR", {
                            timeZone: KST_TIME_ZONE,
                            year: "numeric",
                            month: "long",
                            day: "numeric"
                        }).format(new Date())}</strong></div>
                        {todayGames.length === 0 ? (
                            <p className="today-empty">오늘 예정된 경기가 없습니다.</p>
                        ) : (
                            <div className="today-game-list">
                                {todayGames.map((game) => (
                                    <button key={game.gameId}
                                            className={selectedGame?.gameId === game.gameId ? "today-game active" : "today-game"}
                                            onClick={() => onSelectGame(game)}>
                                        <span
                                            className={`status-pill ${game.bookingStatus.toLowerCase()}`}>{gameStatusMeta[game.bookingStatus].label}</span>
                                        <strong>{game.homeTeam.name} <em>VS</em> {game.awayTeam.name}</strong>
                                        <small>{formatGameDate(game.gameAt)} · {game.stadium.name}</small>
                                    </button>
                                ))}
                            </div>
                        )}
                    </section>
                    <figure className="hero-stadium-photo">
                        <img src={STADIUM_IMAGE_URL} alt="잠실야구장 경기 전경"/>
                        <figcaption><span>JAMSIL</span> 실제 구장 이미지</figcaption>
                    </figure>
                </div>
            </section>

            <section className="schedule-section">
                <div className="section-head">
                    <div><span className="eyebrow">— GAME CALENDAR</span><h2>경기 일정</h2><p>날짜와 구단, 구장을 선택해 전체 예매 상태를
                        확인하세요.</p></div>
                    <button className="outline-button" onClick={onReload} disabled={busy}>↻ 일정 새로고침</button>
                </div>
                <div className="calendar-shell">
                    <div className="calendar-toolbar">
                        <div className="calendar-month-control">
                            <button onClick={() => moveMonth(-1)} aria-label="이전 달">‹</button>
                            <strong>{year}년 {month + 1}월</strong>
                            <button onClick={() => moveMonth(1)} aria-label="다음 달">›</button>
                            <button className="today-button" onClick={showToday}>오늘</button>
                        </div>
                        <div className="schedule-filters">
                            <label><span>구단별</span><select value={teamFilter}
                                                           onChange={(event) => setTeamFilter(event.target.value)}>
                                <option value="ALL">전체 구단</option>
                                {teams.map(([id, name]) => <option key={id} value={id}>{name}</option>)}
                            </select></label>
                            <label><span>구장별</span><select value={stadiumFilter}
                                                           onChange={(event) => setStadiumFilter(event.target.value)}>
                                <option value="ALL">전체 구장</option>
                                {stadiums.map(([id, name]) => <option key={id} value={id}>{name}</option>)}
                            </select></label>
                            <label><span>상태</span><select value={statusFilter}
                                                          onChange={(event) => setStatusFilter(event.target.value)}>
                                <option value="ALL">전체 상태</option>
                                <option value="SCHEDULED">예매 예정</option>
                                <option value="OPEN">예매중</option>
                                <option value="CLOSED">예매 종료</option>
                                <option value="CANCELLED">경기 취소</option>
                            </select></label>
                        </div>
                    </div>
                    <div className="calendar-weekdays">{["일", "월", "화", "수", "목", "금", "토"].map((day) => <span
                        key={day}>{day}</span>)}</div>
                    <div className="calendar-grid">
                        {calendarCells.map((day, index) => {
                            if (!day) return <div className="calendar-day empty" key={`empty-${index}`}/>;
                            const dateKey = toDateKey(createKstCalendarDate(year, month, day));
                            const dayGames = calendarFilteredGames.filter((game) => toDateKey(game.gameAt) === dateKey);
                            const isToday = dateKey === todayKey;
                            return (
                                <button
                                    className={`${selectedDate === dateKey ? "calendar-day selected" : "calendar-day"}${isToday ? " today" : ""}`}
                                    key={dateKey} onClick={() => selectCalendarDay(day)}>
                                    <span>{day}</span>
                                    {dayGames.length > 0 && <strong>{dayGames.length}경기</strong>}
                                    <div>{dayGames.map((game) => <i className={game.bookingStatus.toLowerCase()}
                                                                    key={game.gameId}/>)}</div>
                                </button>
                            );
                        })}
                    </div>
                    <div className="calendar-legend"><span><i className="scheduled"/>예매 예정</span><span><i
                        className="open"/>예매중</span><span><i className="closed"/>예매 종료</span><span><i
                        className="cancelled"/>경기 취소</span></div>
                </div>

                <div className="list-heading">
                    <div>
                        <strong>{selectedDate ? `${selectedDate.replace(/-/g, ".")} 경기` : "전체 경기"}</strong><span>{filteredGames.length}개 일정</span>
                    </div>
                    {selectedDate &&
							<button className="text-button" onClick={() => setSelectedDate(null)}>날짜 선택 해제</button>}
                </div>
                {(gamesResult?.data.length ?? 0) === 0 ? (
                    <EmptyState title="표시할 경기가 없습니다." description="테스트 데이터 준비 스크립트를 실행한 뒤 일정을 새로고침해주세요."/>
                ) : filteredGames.length === 0 ? (
                    <EmptyState title="조건에 맞는 경기가 없습니다." description="날짜 또는 필터를 변경해 다른 경기를 확인해주세요."/>
                ) : (
                    <div className="game-list">
                        {filteredGames.map((game) => {
                            const date = formatShortDate(game.gameAt);
                            const selected = selectedGame?.gameId === game.gameId;
                            const meta = gameStatusMeta[game.bookingStatus];
                            const completed = completedGameIds.has(game.gameId);
                            return (
                                <article
                                    className={`${selected ? "game-card selected" : "game-card"} ${game.bookingStatus.toLowerCase()}`}
                                    key={game.gameId}>
                                    <button className="game-card-main" onClick={() => onSelectGame(game)}>
                                        <div className="game-date">
                                            <span>{date.month}</span><strong>{date.day}</strong><small>{date.weekday}</small>
                                        </div>
                                        <div className="match-info">
                                            <span
                                                className={`status-pill ${game.bookingStatus.toLowerCase()}`}>{completed ? "예매 완료" : meta.label}</span>
                                            <div className="match-teams">
                                                <strong>{game.homeTeam.name}</strong><span>VS</span><strong>{game.awayTeam.name}</strong>
                                            </div>
                                            <p>{game.title}</p>
                                            <small>{formatGameDate(game.gameAt)} · {game.stadium.name} · {meta.description}</small>
                                        </div>
                                    </button>
                                    <button className="card-book-button" onClick={() => {
                                        void onSelectGame(game);
                                    }} disabled={game.bookingStatus !== "OPEN" || completed}>
                                        {completed ? "예매 완료" : selected ? "선택됨" : meta.action}<span>→</span>
                                    </button>
                                </article>
                            );
                        })}
                    </div>
                )}
            </section>
        </>
    );
}

function QueueScreen({game, queue, progress, busy, onRefresh, onCancel, onContinue}: {
    game: GameSummary;
    queue: QueueViewState | null;
    progress: number;
    busy: boolean;
    onRefresh: () => void;
    onCancel: () => void;
    onContinue: () => void;
}) {
    const admitted = Boolean(queue?.queueToken);
    return (
        <section className="queue-page">
            <div className="compact-game-header"><span>{formatShortDate(game.gameAt).day}</span>
                <div><strong>{game.homeTeam.name} <em>VS</em> {game.awayTeam.name}
                </strong><small>{formatGameDate(game.gameAt)} · {game.stadium.name}</small></div>
            </div>
            <div className={admitted ? "queue-card admitted" : "queue-card"}>
                <div className="queue-icon">{admitted ? "✓" : <span className="spinner"/>}</div>
                <span className="eyebrow">VIRTUAL QUEUE</span>
                <h1>{admitted ? "대기가 완료되었습니다." : queue?.registrationPending ? "대기열 등록 중입니다." : "접속 인원이 많아 대기 중입니다."}</h1>
                <p>{admitted ? "입장 토큰이 발급되었습니다. 유효 시간 안에 좌석을 선택해주세요." : "창을 닫지 않으면 순번이 자동으로 갱신됩니다."}</p>

                <div className="queue-rank">
                    <span>나의 대기순서</span>
                    <strong>{admitted ? "입장 가능" : queue?.registrationPending ? "접수 중" : `${queue?.rank.toLocaleString() ?? "-"}번째`}</strong>
                </div>
                <div className="progress-track"><span style={{width: `${progress}%`}}/></div>
                <div className="queue-meta">
                    <div><span>현재 상태</span><strong>{queue?.queueStatus ?? "WAITING"}</strong></div>
                    <div>
                        <span>예상 대기시간</span><strong>{queue?.estimatedWaitSeconds == null ? "계산 중" : `${queue.estimatedWaitSeconds}초`}</strong>
                    </div>
                </div>

                <div className="queue-actions">
                    <button className="outline-button" onClick={onRefresh} disabled={busy}>상태 확인</button>
                    <button className="primary-button" onClick={onContinue} disabled={!admitted || busy}>좌석 선택으로 이동 →
                    </button>
                </div>
                {!admitted &&
						<button className="text-button danger" onClick={onCancel} disabled={busy}>예매 취소하고 돌아가기</button>}
            </div>
            <div className="info-strip"><span>i</span><p><strong>대기 중에는 페이지를 닫지 마세요.</strong> 실시간 연결이 종료되더라도 상태 확인 버튼으로
                현재 순번을 다시 조회할 수 있습니다.</p></div>
        </section>
    );
}

function SeatScreen({
                        game,
                        zonesResult,
                        seatsResult,
                        selectedZoneId,
                        selectedSeatIds,
                        selectedSeats,
                        seatRows,
                        reservation,
                        order,
                        queueTokenExpiresAt,
                        queueTokenAvailable,
                        totalAmount,
                        busy,
                        onSelectZone,
                        onToggleSeat,
                        onReserve,
                        onCancelReservation,
                        onContinue
                    }: {
    game: GameSummary;
    zonesResult: ApiResult<GameZone[]> | null;
    seatsResult: ApiResult<GameSeat[]> | null;
    selectedZoneId: number | null;
    selectedSeatIds: number[];
    selectedSeats: GameSeat[];
    seatRows: Array<[string, GameSeat[]]>;
    reservation: ReservationResponse | null;
    order: OrderResponse | null;
    queueTokenExpiresAt: string | null;
    queueTokenAvailable: boolean;
    totalAmount: number;
    busy: boolean;
    onSelectZone: (zoneId: number) => void;
    onToggleSeat: (seat: GameSeat) => void;
    onReserve: () => void;
    onCancelReservation: () => void;
    onContinue: () => void;
}) {
    const selectedZone = zonesResult?.data.find((zone) => zone.zoneId === selectedZoneId);
    const timerTarget = order?.paymentDeadline ?? reservation?.holdExpiresAt ?? (queueTokenAvailable ? queueTokenExpiresAt : null);
    const [timerExpired, setTimerExpired] = useState(() => isDeadlineExpired(timerTarget));
    const selectionLocked = busy || Boolean(reservation) || timerExpired || !queueTokenAvailable;

    useEffect(() => {
        setTimerExpired(isDeadlineExpired(timerTarget));
    }, [timerTarget]);

    return (
        <section className="seat-page">
            <div className="compact-game-header"><span>{formatShortDate(game.gameAt).day}</span>
                <div><strong>{game.homeTeam.name} <em>VS</em> {game.awayTeam.name}
                </strong><small>{formatGameDate(game.gameAt)} · {game.stadium.name}</small></div>
                <div className="seat-legend"><span className="available"/>선택 가능<span className="picked"/>선택 좌석<span
                    className="unavailable"/>선택 불가
                </div>
            </div>

            <div className="seat-layout">
                <div className="zone-panel">
                    <div className="panel-title"><span>01</span>
                        <div><strong>구역 선택</strong><small>원하는 구역을 먼저 선택하세요.</small></div>
                    </div>
                    <div className="stadium-mini"><img src={STADIUM_IMAGE_URL}
                                                       alt="잠실야구장 경기 전경"/><span>JAMSIL STADIUM</span></div>
                    <div className="zone-list">
                        {(zonesResult?.data ?? []).map((zone) => (
                            <button className={selectedZoneId === zone.zoneId ? "active" : ""} key={zone.zoneId}
                                    onClick={() => onSelectZone(zone.zoneId)} disabled={selectionLocked}>
                                <div>
                                    <strong>{zone.zoneName}</strong><small>{zone.grade === "INFIELD" ? "내야" : "외야"} · {formatPrice(zone.basePrice)}</small>
                                </div>
                                <span>{zone.availableCount}<small> / {zone.totalCount}석</small></span>
                            </button>
                        ))}
                    </div>
                </div>

                <div className="seat-picker-panel">
                    <div className="panel-title"><span>02</span>
                        <div><strong>좌석
                            선택</strong><small>{selectedZone ? `${selectedZone.zoneName}의 실제 좌석을 선택하세요.` : "구역을 선택해주세요."}</small>
                        </div>
                    </div>
                    {seatRows.length === 0 ? (
                        <EmptyState title="표시할 좌석이 없습니다." description="다른 구역을 선택하거나 좌석 상태를 새로 확인해주세요."/>
                    ) : (
                        <div className="seat-map">
                            <div className="field-direction">그라운드 방향</div>
                            {seatRows.map(([rowName, rowSeats]) => (
                                <div className="seat-row" key={rowName}>
                                    <strong>{rowName}</strong>
                                    <div>
                                        {rowSeats.map((seat) => (
                                            <button
                                                key={seat.gameSeatId}
                                                className={`seat ${seat.status.toLowerCase()} ${selectedSeatIds.includes(seat.gameSeatId) ? "picked" : ""}`}
                                                onClick={() => onToggleSeat(seat)}
                                                disabled={seat.status !== "AVAILABLE" || selectionLocked}
                                                aria-label={`${seat.zoneName} ${seat.seatRow}열 ${seat.seatNumber}번 ${formatPrice(seat.price)}`}
                                                aria-pressed={selectedSeatIds.includes(seat.gameSeatId)}
                                                title={`${seat.zoneName} ${seat.seatRow}열 ${seat.seatNumber}번 · ${formatPrice(seat.price)}`}
                                            >{seat.seatNumber}</button>
                                        ))}
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                <aside className="selection-panel">
                    <div className="panel-title"><span>03</span>
                        <div><strong>선택 확인</strong><small>최대 2석까지 선택할 수 있습니다.</small></div>
                    </div>
                    {timerTarget ? (
                        <div className={timerExpired ? "hold-timer expired" : "hold-timer"}>
                            <span>{order ? "결제 남은 시간" : reservation ? "좌석 선점 남은 시간" : "좌석 선택 남은 시간"}</span>
                            <Countdown target={timerTarget} onExpire={() => setTimerExpired(true)}/>
                        </div>
                    ) : (
                        <div className="hold-timer expired"><span>입장 토큰</span><strong>사용 완료</strong></div>
                    )}
                    {timerExpired && <p className="deadline-warning">제한시간이 끝났습니다. 다음 단계로 진행할 수 없습니다.</p>}
                    <div className="selected-seat-list">
                        {selectedSeats.length === 0 ? <p>좌석을 선택하면 이곳에 표시됩니다.</p> : selectedSeats.map((seat) => (
                            <div key={seat.gameSeatId}>
                                <span><strong>{seat.zoneName}</strong><small>{seat.seatRow}열 {seat.seatNumber}번</small></span><strong>{formatPrice(seat.price)}</strong>
                            </div>
                        ))}
                    </div>
                    <div className="total-line"><span>총 결제 예정 금액</span><strong>{formatPrice(totalAmount)}</strong></div>
                    {!reservation ? (
                        <button className="primary-button full" onClick={onReserve}
                                disabled={selectedSeatIds.length === 0 || selectionLocked}>{timerExpired ? "좌석 선택 시간 만료" : `${selectedSeatIds.length}석 선점하기 →`}</button>
                    ) : (
                        <>
                            <button className="primary-button full" onClick={onContinue}
                                    disabled={timerExpired || busy}>{order ? "주문 정보로 돌아가기 →" : "주문 정보 입력 →"}</button>
                            {!order &&
									<button className="outline-button full" onClick={onCancelReservation}
									        disabled={busy}>선점
										해제</button>}
                        </>
                    )}
                    <small
                        className="selection-help">{order ? "주문이 생성된 좌석은 변경할 수 없으며, 결제 기한까지 동일하게 유지됩니다." : "입장 후 5분 안에 좌석을 선점해야 하며, 선점 후에는 예약 만료시간이 적용됩니다."}</small>
                </aside>
            </div>
        </section>
    );
}

function CheckoutScreen({
                            game,
                            seats,
                            reservation,
                            order,
                            paymentPrepared,
                            busy,
                            onCreateOrder,
                            onRefreshOrder,
                            onCancelOrder,
                            onPayment,
                            onBack
                        }: {
    game: GameSummary;
    seats: GameSeat[];
    reservation: ReservationResponse | null;
    order: OrderResponse | null;
    paymentPrepared: boolean;
    busy: boolean;
    onCreateOrder: () => void;
    onRefreshOrder: () => void;
    onCancelOrder: () => void;
    onPayment: () => void;
    onBack: () => void;
}) {
    const amount = order?.totalAmount ?? seats.reduce((sum, seat) => sum + seat.price, 0);
    const deadlineTarget = order?.paymentDeadline ?? reservation?.holdExpiresAt ?? null;
    const [deadlineExpired, setDeadlineExpired] = useState(() => isDeadlineExpired(deadlineTarget));

    useEffect(() => {
        setDeadlineExpired(isDeadlineExpired(deadlineTarget));
    }, [deadlineTarget]);

    return (
        <section className="checkout-page">
            <div className="page-title">
                <button className="step-back-button" onClick={onBack} disabled={busy}>← 좌석 선택으로</button>
                <span className="eyebrow">CHECKOUT</span><h1>예매 정보 확인</h1><p>선택한 경기와 좌석을 확인한 뒤 주문을 생성해주세요.</p></div>
            <div className="checkout-layout">
                <div className="checkout-details">
                    <article className="detail-card game-summary"><img src={STADIUM_IMAGE_URL} alt="잠실야구장 경기 전경"/>
                        <div><span>경기 정보</span><h2>{game.homeTeam.name} <em>VS</em> {game.awayTeam.name}</h2>
                            <p>{formatGameDate(game.gameAt)}</p><small>{game.stadium.name}</small></div>
                    </article>
                    <article className="detail-card">
                        <div className="detail-card-head"><h2>선택 좌석</h2><span>{seats.length}석</span></div>
                        {seats.map((seat) => <div className="checkout-seat" key={seat.gameSeatId}>
                            <span><strong>{seat.zoneName}</strong><small>{seat.seatRow}열 {seat.seatNumber}번</small></span><strong>{formatPrice(seat.price)}</strong>
                        </div>)}</article>
                    <article className="detail-card">
                        <div className="detail-card-head"><h2>수령 방법</h2><span>MOBILE</span></div>
                        <div className="delivery-option active"><span>▣</span>
                            <div><strong>모바일 티켓</strong><small>결제 완료 후 내 티켓에서 확인</small></div>
                            <b>✓</b></div>
                    </article>
                </div>

                <aside className="payment-summary">
                    <h2>결제 금액</h2>
                    <dl>
                        <dt>좌석 금액</dt>
                        <dd>{formatPrice(amount)}</dd>
                        <dt>예매 수수료</dt>
                        <dd>0원</dd>
                    </dl>
                    <div className="total-line"><span>최종 결제금액</span><strong>{formatPrice(amount)}</strong></div>
                    {deadlineTarget && <div className={deadlineExpired ? "deadline-line expired" : "deadline-line"}>
						<span>{order ? "결제 남은 시간" : "선점 남은 시간"}</span><Countdown target={deadlineTarget}
						                                                         onExpire={() => setDeadlineExpired(true)}/>
					</div>}
                    {deadlineExpired && <p className="deadline-warning">제한시간이 만료되어 더 이상 진행할 수 없습니다.</p>}
                    {!order ? (
                        <button className="primary-button full" onClick={onCreateOrder}
                                disabled={!reservation || busy || deadlineExpired}>주문 생성하기 →</button>
                    ) : (
                        <>
                            <div className={`order-status ${order.status.toLowerCase()}`}>
                                <span>주문번호</span><strong>{order.orderNo}</strong><small>{order.status}</small></div>
                            <button className="primary-button full" onClick={onPayment}
                                    disabled={order.status !== "CREATED" || busy || deadlineExpired}>{deadlineExpired ? "결제 시간 만료" : paymentPrepared ? "결제 화면으로 이동 →" : `${formatPrice(order.totalAmount)} 결제 준비 →`}</button>
                            <button className="outline-button full" onClick={onRefreshOrder} disabled={busy}>주문 상태 확인
                            </button>
                            <button className="text-button danger" onClick={onCancelOrder}
                                    disabled={order.status !== "CREATED" || busy}>주문 취소
                            </button>
                        </>
                    )}
                    <p className="terms-copy">결제 버튼 클릭 시 이용약관과 취소 정책에 동의합니다.</p>
                </aside>
            </div>
        </section>
    );
}

function PaymentScreen({game, order, payment, busy, onOpenPayment, onRefreshOrder, onTickets, onBack}: {
    game: GameSummary | null;
    order: OrderResponse | null;
    payment: PaymentCreateResponse | null;
    busy: boolean;
    onOpenPayment: () => void;
    onRefreshOrder: () => void;
    onTickets: () => void;
    onBack: () => void;
}) {
    const approved = payment?.status === "APPROVED" || order?.status === "PAID";
    const [deadlineExpired, setDeadlineExpired] = useState(() => isDeadlineExpired(order?.paymentDeadline));

    useEffect(() => {
        setDeadlineExpired(isDeadlineExpired(order?.paymentDeadline));
    }, [order?.paymentDeadline]);

    return (
        <section className="payment-page">
            <div className={approved ? "payment-result approved" : "payment-result"}>
                {!approved &&
						<button className="step-back-button" onClick={onBack} disabled={busy}>← 주문으로 돌아가기</button>}
                <div className="result-symbol">{approved ? "✓" : "₩"}</div>
                <span className="eyebrow">{approved ? "BOOKING COMPLETE" : "PAYMENT"}</span>
                <h1>{approved ? "예매가 완료되었습니다!" : "결제를 완료해주세요."}</h1>
                <p>{approved ? "결제와 좌석 확정이 완료되었습니다." : "Toss 결제창에서 카드 인증을 마치면 서버가 최종 승인합니다."}</p>
                {!approved && order?.paymentDeadline && (
                    <div className={deadlineExpired ? "payment-deadline expired" : "payment-deadline"}>
                        <span>결제 남은 시간</span>
                        <Countdown target={order.paymentDeadline} onExpire={() => setDeadlineExpired(true)}/>
                    </div>
                )}
                {!approved && deadlineExpired &&
						<p className="deadline-warning centered">결제시간이 만료되었습니다. 주문 상태를 확인해주세요.</p>}
                <div className="payment-receipt">
                    <div><span>경기</span><strong>{game?.title ?? "-"}</strong></div>
                    <div><span>주문번호</span><strong>{order?.orderNo ?? "-"}</strong></div>
                    <div><span>결제번호</span><strong>{payment?.paymentNo ?? "-"}</strong></div>
                    <div><span>결제 상태</span><strong>{payment?.status ?? "준비 전"}</strong></div>
                    <div className="receipt-total">
                        <span>총 결제금액</span><strong>{formatPrice(payment?.amount ?? order?.totalAmount ?? 0)}</strong>
                    </div>
                </div>
                {approved ? (
                    <button className="primary-button" onClick={onTickets}>내 티켓 확인 →</button>
                ) : (
                    <div className="payment-actions">
                        <button className="primary-button" onClick={onOpenPayment}
                                disabled={!payment || payment.status !== "READY" || busy || deadlineExpired}>{deadlineExpired ? "결제 시간 만료" : "Toss 결제창 열기 →"}</button>
                        <button className="outline-button" onClick={onRefreshOrder} disabled={!order || busy}>주문 상태 확인
                        </button>
                    </div>
                )}
            </div>
        </section>
    );
}

function TicketScreen({result, games, busy, onReload}: {
    result: ApiResult<TicketSummary[]> | null;
    games: GameSummary[];
    busy: boolean;
    onReload: () => void;
}) {
    return (
        <section className="ticket-page">
            <div className="section-head">
                <div><span className="eyebrow">MY TICKETS</span><h1>내 티켓</h1><p>결제 완료 후 발급된 모바일 티켓을 확인합니다.</p></div>
                <button className="outline-button" onClick={onReload} disabled={busy}>↻ 티켓 새로고침</button>
            </div>
            {result?.source === "mock" && (
                <div className="mock-notice"><span>MOCK</span>{result.message}</div>
            )}
            {(result?.data.length ?? 0) === 0 ? (
                <EmptyState title="보유한 티켓이 없습니다." description="경기 예매와 결제를 완료하면 이곳에 티켓이 표시됩니다."/>
            ) : (
                <div className="ticket-list">
                    {result?.data.map((ticket) => {
                        const gameTitle = games.find((game) => game.gameId === ticket.gameId)?.title
                            ?? `경기 #${ticket.gameId}`;
                        return (
                            <article className="ticket-card" key={ticket.ticketId}>
                                <div className="ticket-accent"><span>Re:<b>Seat</b></span><small>ADMIT ONE</small></div>
                                <div className="ticket-info"><span
                                    className={`status-pill ${ticket.status.toLowerCase()}`}>{ticket.status}</span>
                                    <h2>{gameTitle}</h2><p>{ticket.seat}</p>
                                    <small>{formatGameDate(ticket.gameAt)}</small><strong>{ticket.ticketNo}</strong>
                                </div>
                                <div className="qr-box"><span>QR</span><small>{ticket.qrToken}</small></div>
                            </article>
                        );
                    })}
                </div>
            )}
        </section>
    );
}

export default App;
