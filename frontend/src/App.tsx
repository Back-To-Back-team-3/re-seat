import { useEffect, useMemo, useState } from "react";
import {
  cancelOrder,
  cancelReservation,
  completePayment,
  createOrder,
  createReservation,
  admitQueue,
  enterQueue,
  failPayment,
  getGames,
  getGame,
  getGameSeats,
  getGameZones,
  getOrder,
  getQueueStatus,
  getReservationHoldTime,
  getTickets,
  login,
  requestPayment,
  signUp,
  streamQueue
} from "./api/services";
import { clearTokens, getAccessTokenRole, setQueueToken } from "./api/client";
import type {
  ApiResult,
  GameSeat,
  GameZone,
  GameSummary,
  OrderResponse,
  PaymentCreateResponse,
  QueueEnterResponse,
  ReservationResponse,
  TicketSummary
} from "./types";

type Step = "games" | "queue" | "seats" | "order" | "payment" | "tickets";

const steps: Array<{ id: Step; label: string }> = [
  { id: "games", label: "경기" },
  { id: "queue", label: "대기열" },
  { id: "seats", label: "좌석" },
  { id: "order", label: "주문" },
  { id: "payment", label: "결제" },
  { id: "tickets", label: "티켓" }
];

const TOSS_CLIENT_KEY = import.meta.env.VITE_TOSS_CLIENT_KEY ?? "";
const PAYMENT_WINDOW_SECONDS = 8 * 60;
const PENDING_PAYMENT_KEY = "pendingTossPayment";
const PAYMENT_CALLBACK_KEY_PREFIX = "tossPaymentCallback:";

function getInitialStep(): Step {
  const params = new URLSearchParams(window.location.search);
  const hasPaymentResult = params.has("paymentId")
    && (params.has("paymentKey") || params.has("code"));

  return hasPaymentResult ? "payment" : "games";
}

function formatPrice(value: number) {
  return new Intl.NumberFormat("ko-KR").format(value) + "원";
}

function SourceNotice({ result }: { result?: ApiResult<unknown> | null }) {
  if (!result || result.source !== "mock") {
    return null;
  }
  return <div className="notice">{result.message ?? "샘플 데이터로 표시 중입니다."}</div>;
}

function parseLocalDateTime(value?: string | null) {
  if (!value) {
    return null;
  }

  const normalized = value.replace("T", " ").split(".")[0];
  const match = normalized.match(/^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2})(?::(\d{2}))?/);

  if (match) {
    const [, year, month, day, hour, minute, second = "0"] = match;
    return new Date(
      Number(year),
      Number(month) - 1,
      Number(day),
      Number(hour),
      Number(minute),
      Number(second)
    );
  }

  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

function formatCountdown(target?: string | null, maxMinutes?: number) {
  const targetDate = parseLocalDateTime(target);
  if (!targetDate) {
    return "-";
  }

  let diff = targetDate.getTime() - Date.now();
  if (diff <= 0) {
    return "만료";
  }

  if (maxMinutes && diff > maxMinutes * 60 * 1000) {
    diff = maxMinutes * 60 * 1000;
  }

  const totalSeconds = Math.floor(diff / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;

  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

function Countdown({ target, maxMinutes }: { target?: string | null; maxMinutes?: number }) {
  const [left, setLeft] = useState(formatCountdown(target, maxMinutes));

  useEffect(() => {
    setLeft(formatCountdown(target, maxMinutes));
    const timerId = window.setInterval(() => {
      setLeft(formatCountdown(target, maxMinutes));
    }, 1000);

    return () => window.clearInterval(timerId);
  }, [target, maxMinutes]);

  return <span className={left === "만료" ? "timer expired" : "timer"}>{left}</span>;
}

function DeadlineCountdown({ deadlineAt }: { deadlineAt: number | null }) {
  const calculateSeconds = () => deadlineAt === null
    ? null
    : Math.max(0, Math.ceil((deadlineAt - Date.now()) / 1000));
  const [left, setLeft] = useState(calculateSeconds);

  useEffect(() => {
    setLeft(calculateSeconds());
    if (deadlineAt === null) return;

    const timerId = window.setInterval(() => {
      setLeft(calculateSeconds());
    }, 1000);

    return () => window.clearInterval(timerId);
  }, [deadlineAt]);

  if (left === null) return <span className="timer">-</span>;
  if (left === 0) return <span className="timer expired">만료</span>;

  const minutes = Math.floor(left / 60);
  const remainingSeconds = left % 60;
  return <span className="timer">{minutes}:{remainingSeconds.toString().padStart(2, "0")}</span>;
}

function App() {
  const [activeStep, setActiveStep] = useState<Step>(getInitialStep);
  const [theme, setTheme] = useState(localStorage.getItem("theme") ?? "light");
  const [isAuthed, setIsAuthed] = useState(Boolean(localStorage.getItem("accessToken")));
  const [authedEmail, setAuthedEmail] = useState(localStorage.getItem("userEmail") ?? "");
  const [authedRole, setAuthedRole] = useState(
    localStorage.getItem("userRole") ?? (isAuthed ? getAccessTokenRole() : "")
  );
  const [authMode, setAuthMode] = useState<"login" | "signup">("login");
  const [email, setEmail] = useState("user@example.com");
  const [password, setPassword] = useState("password1234");
  const [name, setName] = useState("User");
  const [nickname, setNickname] = useState("user");
  const [phone, setPhone] = useState("010-1234-5678");
  const [admitLimit, setAdmitLimit] = useState(20);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const [gamesResult, setGamesResult] = useState<ApiResult<GameSummary[]> | null>(null);
  const [selectedGame, setSelectedGame] = useState<GameSummary | null>(null);
  const [queueResult, setQueueResult] = useState<ApiResult<QueueEnterResponse> | null>(null);
  const [zonesResult, setZonesResult] = useState<ApiResult<GameZone[]> | null>(null);
  const [selectedZoneId, setSelectedZoneId] = useState<number | null>(null);
  const [seatsResult, setSeatsResult] = useState<ApiResult<GameSeat[]> | null>(null);
  const [selectedSeatIds, setSelectedSeatIds] = useState<number[]>([]);
  const [reservationResult, setReservationResult] = useState<ApiResult<ReservationResponse> | null>(null);
  const [holdDeadlineAt, setHoldDeadlineAt] = useState<number | null>(null);
  const [orderResult, setOrderResult] = useState<ApiResult<OrderResponse> | null>(null);
  const [paymentDeadlineAt, setPaymentDeadlineAt] = useState<number | null>(null);
  const [paymentResult, setPaymentResult] = useState<ApiResult<PaymentCreateResponse> | null>(null);
  const [ticketsResult, setTicketsResult] = useState<ApiResult<TicketSummary[]> | null>(null);

  const selectedSeats = useMemo(
    () => (seatsResult?.data ?? []).filter((seat) => selectedSeatIds.includes(seat.gameSeatId)),
    [seatsResult, selectedSeatIds]
  );

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

  useEffect(() => {
    loadGames();
  }, []);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem("theme", theme);
  }, [theme]);

  useEffect(() => {
    if (!isAuthed || !selectedGame || activeStep !== "queue" || queueResult?.data.queueToken) {
      return;
    }

    const controller = new AbortController();
    streamQueue(selectedGame.gameId, {
      onRank: (status) => {
        setQueueResult((current) => ({
          data: {
            gameId: selectedGame.gameId,
            rank: status.rank,
            estimatedWaitSeconds: status.estimatedWaitSeconds,
            queueStatus: status.queueStatus,
            admitted: status.admitted,
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
            queueToken: event.queueToken,
            tokenExpiresAt: event.tokenExpiresAt
          },
          source: "api"
        });
        setToast("입장이 허용되었습니다. 좌석을 선택할 수 있습니다.");
      }
    }, controller.signal).catch((streamError) => {
      if (streamError instanceof DOMException && streamError.name === "AbortError") return;
      setError(streamError instanceof Error ? streamError.message : "대기열 실시간 연결이 종료되었습니다.");
    });

    return () => controller.abort();
  }, [activeStep, isAuthed, queueResult?.data.queueToken, selectedGame]);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const paymentId = Number(params.get("paymentId"));
    const paymentKey = params.get("paymentKey");
    const orderId = params.get("orderId");
    const amount = Number(params.get("amount"));
    const code = params.get("code");
    const message = params.get("message");

    if (!paymentId || (!paymentKey && !code)) {
      return;
    }

    const callbackKey = `${PAYMENT_CALLBACK_KEY_PREFIX}${paymentId}:${paymentKey ?? code}`;
    if (sessionStorage.getItem(callbackKey)) {
      window.history.replaceState({}, "", window.location.pathname);
      return;
    }

    sessionStorage.setItem(callbackKey, "processing");
    window.history.replaceState({}, "", window.location.pathname);

    run(async () => {
      try {
        const pendingPayment = JSON.parse(
          sessionStorage.getItem(PENDING_PAYMENT_KEY) ?? "null"
        ) as { orderId: number; payment: PaymentCreateResponse } | null;

        if (paymentKey && orderId && amount) {
          await completePayment(paymentId, { paymentKey, orderId, amount });
          sessionStorage.setItem(callbackKey, "completed");

          if (pendingPayment) {
            const order = await getOrder(pendingPayment.orderId);
            setOrderResult(order);
            setPaymentResult({
              data: { ...pendingPayment.payment, status: "APPROVED" },
              source: "api"
            });
          }

          setPaymentDeadlineAt(Date.now());
          setActiveStep("payment");
          setToast("결제 승인 처리가 완료되었습니다.");
        } else if (code && message && orderId) {
          await failPayment(paymentId, { code, message, orderId });
          sessionStorage.setItem(callbackKey, "completed");
          setPaymentResult(pendingPayment ? {
            data: { ...pendingPayment.payment, status: "FAILED" },
            source: "api"
          } : null);
          setActiveStep("payment");
          setError(`결제 실패: ${message}`);
          sessionStorage.setItem(callbackKey, "completed");
        }
      } catch (callbackError) {
        if (sessionStorage.getItem(callbackKey) !== "completed") {
          sessionStorage.removeItem(callbackKey);
        }
        throw callbackError;
      } finally {
        sessionStorage.removeItem(PENDING_PAYMENT_KEY);
      }
    });
  }, []);

  async function run<T>(action: () => Promise<T>, successMessage?: string) {
    setBusy(true);
    setError(null);
    try {
      const result = await action();
      if (successMessage) {
        setToast(successMessage);
      }
      return result;
    } catch (err) {
      setError(err instanceof Error ? err.message : "요청 처리 중 문제가 발생했습니다.");
      return null;
    } finally {
      setBusy(false);
    }
  }

  async function loadGames() {
    const result = await run(() => getGames());
    if (result) {
      setGamesResult(result);
      setSelectedGame((current) => current ?? result.data[0] ?? null);
    }
  }

  async function handleAuth() {
    await run(async () => {
      if (authMode === "signup") {
        await signUp({ email, password, name, nickname, phone });
        setAuthMode("login");
        setToast("회원가입이 완료되었습니다. 가입한 계정으로 로그인해주세요.");
        return;
      }
      const response = await login(email, password);
      const userRole = getAccessTokenRole();
      localStorage.setItem("userEmail", email);
      localStorage.setItem("userRole", userRole);
      setAuthedEmail(email);
      setAuthedRole(userRole);
      setIsAuthed(true);
    }, authMode === "signup" ? undefined : "로그인했습니다.");
  }

  async function handleEnterQueue() {
    if (!selectedGame) return;
    setQueueToken(null);
    setQueueResult(null);
    setZonesResult(null);
    setSelectedZoneId(null);
    setSeatsResult(null);
    setSelectedSeatIds([]);
    setReservationResult(null);
    setHoldDeadlineAt(null);
    setOrderResult(null);
    setPaymentDeadlineAt(null);
    setPaymentResult(null);
    setTicketsResult(null);
    const result = await run(() => enterQueue(selectedGame.gameId), "대기열에 진입했습니다.");
    if (result) {
      setQueueResult(result);
      setActiveStep("queue");
    }
  }

  async function handleRefreshQueue() {
    if (!selectedGame) return;
    const result = await run(() => getQueueStatus(selectedGame.gameId));
    if (result) {
      if (result.data.admitted) {
        const admittedResult = await run(() => enterQueue(selectedGame.gameId));
        if (admittedResult) setQueueResult(admittedResult);
      } else {
        setQueueResult(result);
      }
    }
  }

  async function handleSelectGame(game: GameSummary) {
    const result = await run(() => getGame(game.gameId));
    setSelectedGame(result?.data ?? game);
  }

  async function handleAdmitQueue() {
    if (!selectedGame) return;
    const result = await run(() => admitQueue(selectedGame.gameId, admitLimit), `${admitLimit}명 입장 허용을 요청했습니다.`);
    if (result) {
      setQueueResult(result);
    }
  }

  async function handleLoadSeats() {
    if (!selectedGame) return;
    if (!queueResult?.data.queueToken) {
      setError("입장 토큰이 발급된 후 좌석을 조회할 수 있습니다.");
      return;
    }
    setSelectedSeatIds([]);
    setReservationResult(null);
    setHoldDeadlineAt(null);
    setOrderResult(null);
    setPaymentDeadlineAt(null);
    setPaymentResult(null);
    const result = await run(async () => {
      const zones = await getGameZones(selectedGame.gameId);
      const zoneId = selectedZoneId && zones.data.some((zone) => zone.zoneId === selectedZoneId)
        ? selectedZoneId
        : zones.data[0]?.zoneId;
      const seats = await getGameSeats(selectedGame.gameId, zoneId);
      return { zones, seats, zoneId: zoneId ?? null };
    }, "좌석 정보를 불러왔습니다.");
    if (result) {
      setZonesResult(result.zones);
      setSelectedZoneId(result.zoneId);
      setSeatsResult(result.seats);
      setActiveStep("seats");
    }
  }

  async function handleSelectZone(zoneId: number) {
    if (!selectedGame || zoneId === selectedZoneId) return;
    if (reservationResult) {
      setError("선점한 좌석을 해제한 뒤 다른 구역을 선택할 수 있습니다.");
      return;
    }
    const result = await run(() => getGameSeats(selectedGame.gameId, zoneId));
    if (result) {
      setSelectedZoneId(zoneId);
      setSeatsResult(result);
      setSelectedSeatIds([]);
      setReservationResult(null);
      setHoldDeadlineAt(null);
    }
  }

  async function handleReserveSeats() {
    if (!selectedGame || selectedSeatIds.length === 0) return;
    setOrderResult(null);
    setPaymentResult(null);
    const result = await run(
      () => createReservation(selectedGame.gameId, selectedSeatIds),
      "좌석 선점이 완료되었습니다."
    );
    if (result) {
      setReservationResult(result);
      const holdTime = await run(() => getReservationHoldTime(result.data.reservationId));
      setHoldDeadlineAt(
        holdTime ? Date.now() + holdTime.remainingSeconds * 1000 : null
      );
    }
  }

  async function handleCancelReservation() {
    if (!reservationResult) return;
    const result = await run(
      () => cancelReservation(reservationResult.data.reservationId),
      "좌석 선점이 해제되었습니다."
    );
    if (result) {
      setReservationResult(null);
      setHoldDeadlineAt(null);
      setSelectedSeatIds([]);
      if (selectedGame) {
        const seats = await run(() => getGameSeats(selectedGame.gameId, selectedZoneId ?? undefined));
        if (seats) setSeatsResult(seats);
      }
    }
  }

  async function handleCreateOrder() {
    const reservationId = reservationResult?.data.reservationId;
    if (!reservationId) return;

    const result = await run(
      () => createOrder(reservationId, "MOBILE"),
      "주문이 생성되었습니다."
    );
    if (result) {
      setOrderResult(result);
      setPaymentDeadlineAt(Date.now() + PAYMENT_WINDOW_SECONDS * 1000);
      setActiveStep("order");
    }
  }

  async function handleCancelOrder() {
    if (!orderResult) return;
    const result = await run(() => cancelOrder(orderResult.data.orderId), "주문이 취소되었습니다.");
    if (result) {
      setOrderResult({
        ...orderResult,
        data: { ...orderResult.data, status: result.data.status }
      });
      setPaymentDeadlineAt(Date.now());
    }
  }

  async function handlePayment() {
    if (!orderResult) return;
    const result = await run(() => requestPayment(orderResult.data.orderId, "CARD"), "결제 요청이 생성되었습니다.");
    if (result) {
      setPaymentResult(result);
      setActiveStep("payment");
    }
  }

  async function handleOpenTossPayment() {
    if (!paymentResult) return;
    if (!TOSS_CLIENT_KEY) {
      setError("Toss 클라이언트 키가 필요합니다. frontend/.env에 VITE_TOSS_CLIENT_KEY를 설정해주세요.");
      return;
    }
    if (!window.TossPayments) {
      setError("Toss SDK를 불러오지 못했습니다. 네트워크 연결 또는 SDK 스크립트를 확인해주세요.");
      return;
    }

    const payment = paymentResult.data;
    const baseUrl = window.location.origin + window.location.pathname;
    const tossPayments = window.TossPayments(TOSS_CLIENT_KEY);

    sessionStorage.setItem(PENDING_PAYMENT_KEY, JSON.stringify({
      orderId: orderResult?.data.orderId ?? payment.orderId,
      payment
    }));

    await run(() =>
      tossPayments.requestPayment("카드", {
        amount: payment.amount,
        orderId: payment.pgOrderId,
        orderName: "Re-Seat 티켓",
        customerName: authedEmail || "Re-Seat 사용자",
        customerEmail: authedEmail || undefined,
        successUrl: `${baseUrl}?paymentId=${payment.paymentId}`,
        failUrl: `${baseUrl}?paymentId=${payment.paymentId}`
      })
    );
  }

  async function handleLoadTickets() {
    const result = await run(() => getTickets());
    if (result) {
      setTicketsResult(result);
      setActiveStep("tickets");
    }
  }

  function toggleSeat(seat: GameSeat) {
    if (seat.status !== "AVAILABLE") return;
    if (reservationResult) {
      setError("선점한 좌석을 해제한 뒤 좌석 선택을 변경할 수 있습니다.");
      return;
    }
    setReservationResult(null);
    setHoldDeadlineAt(null);
    setOrderResult(null);
    setPaymentDeadlineAt(null);
    setPaymentResult(null);
    setSelectedSeatIds((prev) => {
      if (prev.includes(seat.gameSeatId)) {
        return prev.filter((id) => id !== seat.gameSeatId);
      }
      if (prev.length >= 2) {
        setError("좌석은 최대 2개까지 선택할 수 있습니다.");
        return prev;
      }
      return [...prev, seat.gameSeatId];
    });
  }

  function logout() {
    clearTokens();
    localStorage.removeItem("userEmail");
    localStorage.removeItem("userRole");
    setIsAuthed(false);
    setAuthedEmail("");
    setAuthedRole("");
    setQueueResult(null);
    setZonesResult(null);
    setSelectedZoneId(null);
    setSeatsResult(null);
    setSelectedSeatIds([]);
    setReservationResult(null);
    setHoldDeadlineAt(null);
    setOrderResult(null);
    setPaymentResult(null);
    setTicketsResult(null);
    setToast("로그아웃했습니다.");
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <div>
          <div className="brand">Re-Seat</div>
          <p>경기 관람의 시작, Re-Seat</p>
        </div>
        <div className="topbar-actions">
          <button
            className="ghost-button"
            onClick={() => setTheme((current) => current === "dark" ? "light" : "dark")}
          >
            {theme === "dark" ? "일반모드" : "다크모드"}
          </button>
          {isAuthed ? (
            <>
              <span className="auth-badge">{authedRole || "USER"}</span>
              <button className="ghost-button" onClick={logout}>로그아웃</button>
            </>
          ) : (
            <span className="auth-badge">로그인 필요</span>
          )}
        </div>
      </header>

      <main className="layout">
        <aside className="sidebar">
          <section className="panel auth-panel">
            <h2>계정</h2>
            {isAuthed ? (
              <div className="auth-state">
                <strong>로그인됨</strong>
                <span>{authedEmail}</span>
                <span>{authedRole || "USER"}</span>
                <button className="ghost-button full" onClick={logout}>로그아웃</button>
              </div>
            ) : (
              <>
                <div className="segmented">
                  <button className={authMode === "login" ? "active" : ""} onClick={() => setAuthMode("login")}>로그인</button>
                  <button className={authMode === "signup" ? "active" : ""} onClick={() => setAuthMode("signup")}>회원가입</button>
                </div>
                <label>
                  이메일
                  <input value={email} onChange={(event) => setEmail(event.target.value)} />
                </label>
                <label>
                  비밀번호
                  <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} />
                </label>
                {authMode === "signup" && (
                  <>
                    <label>
                      이름
                      <input value={name} onChange={(event) => setName(event.target.value)} />
                    </label>
                    <label>
                      닉네임
                      <input value={nickname} onChange={(event) => setNickname(event.target.value)} />
                    </label>
                    <label>
                      전화번호
                      <input value={phone} onChange={(event) => setPhone(event.target.value)} />
                    </label>
                  </>
                )}
                <button className="primary-button full" onClick={handleAuth} disabled={busy}>
                  {authMode === "signup" ? "회원가입" : "로그인"}
                </button>
              </>
            )}
          </section>

          <nav className="step-list">
            {steps.map((step, index) => (
              <button
                key={step.id}
                className={activeStep === step.id ? "active" : ""}
                onClick={() => setActiveStep(step.id)}
              >
                <span>{index + 1}</span>
                {step.label}
              </button>
            ))}
          </nav>
        </aside>

        <section className="content">
          {(error || toast) && (
            <div className={error ? "alert error" : "alert success"}>
              {error ?? toast}
              <button onClick={() => { setError(null); setToast(null); }}>닫기</button>
            </div>
          )}

          {activeStep === "games" && (
            <section className="workspace">
              <div className="section-head">
                <div>
                  <h1>경기 선택</h1>
                  <p>예매 가능한 경기를 선택하고 대기열에 진입합니다.</p>
                </div>
                <button className="ghost-button" onClick={loadGames} disabled={busy}>새로고침</button>
              </div>
              <SourceNotice result={gamesResult} />
              <div className="game-grid">
                {(gamesResult?.data ?? []).map((game) => (
                  <button
                    key={game.gameId}
                    className={`game-card ${selectedGame?.gameId === game.gameId ? "selected" : ""}`}
                    onClick={() => handleSelectGame(game)}
                  >
                    <span className={`status-pill ${game.bookingStatus.toLowerCase()}`}>{game.bookingStatus}</span>
                    <strong>{game.title}</strong>
                    <small>{game.stadium?.name ?? "구장 정보 없음"}</small>
                    <span>{game.gameAt}</span>
                  </button>
                ))}
              </div>
              <div className="action-row">
                <button className="primary-button" onClick={handleEnterQueue} disabled={!isAuthed || !selectedGame || busy}>
                  대기열 진입
                </button>
              </div>
            </section>
          )}

          {activeStep === "queue" && (
            <section className="workspace two-column">
              <div>
                <h1>대기열</h1>
                <p>입장이 허용되면 좌석 선택으로 이동합니다.</p>
                <SourceNotice result={queueResult} />
                <div className="metric-board">
                  <div><span>순번</span><strong>{queueResult?.data.rank ?? "-"}</strong></div>
                  <div><span>상태</span><strong>{queueResult?.data.queueStatus ?? "-"}</strong></div>
                  <div><span>입장</span><strong>{queueResult?.data.admitted ? "허용" : "대기"}</strong></div>
                </div>
                <div className="action-row">
                  <button className="ghost-button" onClick={handleRefreshQueue} disabled={!selectedGame || busy}>상태 조회</button>
                  <button className="primary-button" onClick={handleLoadSeats} disabled={!queueResult?.data.queueToken || busy}>
                    좌석 선택으로 이동
                  </button>
                </div>
                {authedRole === "ADMIN" && (
                  <div className="admin-tools">
                    <h2>관리자 입장 처리</h2>
                    <label>
                      입장 허용 인원
                      <input
                        type="number"
                        min="1"
                        max="100"
                        value={admitLimit}
                        onChange={(event) => setAdmitLimit(Number(event.target.value))}
                      />
                    </label>
                    <button className="ghost-button" onClick={handleAdmitQueue} disabled={!selectedGame || busy}>
                      선택 인원 입장 허용
                    </button>
                  </div>
                )}
              </div>
              <div className="flow-box">
                <h2>토큰 상태</h2>
                <p>{queueResult?.data.queueToken ? "입장 토큰 발급 완료" : "입장 토큰 없음"}</p>
                <small>{queueResult?.data.tokenExpiresAt ?? "입장 허용 후 좌석 API 요청에 사용됩니다."}</small>
              </div>
            </section>
          )}

          {activeStep === "seats" && (
            <section className="workspace">
              <div className="section-head">
                <div>
                  <h1>좌석 선택</h1>
                  <p>구역별 좌석을 선택하고 선점 요청을 진행합니다.</p>
                </div>
                <button className="ghost-button" onClick={handleLoadSeats} disabled={!selectedGame || busy}>좌석 불러오기</button>
              </div>
              <SourceNotice result={seatsResult} />
              <div className="zone-list" aria-label="좌석 구역">
                {(zonesResult?.data ?? []).map((zone) => (
                  <button
                    key={zone.zoneId}
                    className={selectedZoneId === zone.zoneId ? "active" : ""}
                    onClick={() => handleSelectZone(zone.zoneId)}
                    disabled={busy}
                  >
                    <strong>{zone.zoneName}</strong>
                    <span>{zone.availableCount}/{zone.totalCount}석</span>
                  </button>
                ))}
              </div>
              <div className="seat-area">
                <div className="seat-map" aria-label="좌석 배치도">
                  {seatRows.map(([rowName, rowSeats]) => (
                    <div className="seat-row" key={rowName}>
                      <strong className="seat-row-label">{rowName}</strong>
                      <div className="seat-grid">
                        {rowSeats.map((seat) => (
                          <button
                            key={seat.gameSeatId}
                            className={`seat ${seat.status.toLowerCase()} ${selectedSeatIds.includes(seat.gameSeatId) ? "picked" : ""}`}
                            onClick={() => toggleSeat(seat)}
                            disabled={seat.status !== "AVAILABLE"}
                            title={`${seat.zoneName} ${seat.seatRow}열 ${seat.seatNumber}번 ${formatPrice(seat.price)}`}
                          >
                            {seat.seatNumber}
                          </button>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
                <aside className="selection-panel">
                  <h2>선택 좌석</h2>
                  {reservationResult && (
                    <div className="timer-box">
                      <span>좌석 선점 남은 시간</span>
                      <strong><DeadlineCountdown deadlineAt={holdDeadlineAt} /></strong>
                    </div>
                  )}
                  {selectedSeats.length === 0 ? (
                    <p>선택된 좌석이 없습니다.</p>
                  ) : (
                    selectedSeats.map((seat) => (
                      <div className="selected-seat" key={seat.gameSeatId}>
                        <span>{seat.zoneName} {seat.seatRow}열 {seat.seatNumber}번</span>
                        <strong>{formatPrice(seat.price)}</strong>
                      </div>
                    ))
                  )}
                  <div className="total-line">
                    <span>합계</span>
                    <strong>{formatPrice(totalAmount)}</strong>
                  </div>
                  <button className="primary-button full" onClick={handleReserveSeats} disabled={selectedSeatIds.length === 0 || busy}>
                    좌석 선점
                  </button>
                  {reservationResult && (
                    <>
                      <button className="ghost-button full" onClick={handleCancelReservation} disabled={busy}>선점 해제</button>
                      <button className="ghost-button full" onClick={() => setActiveStep("order")}>주문으로 이동</button>
                    </>
                  )}
                </aside>
              </div>
            </section>
          )}

          {activeStep === "order" && (
            <section className="workspace two-column">
              <div>
                <h1>주문</h1>
                <p>선점된 좌석 기준으로 주문을 생성하고 결제 전 취소할 수 있습니다.</p>
                <SourceNotice result={reservationResult} />
                <SourceNotice result={orderResult} />
                <div className="summary-card">
                  <h2>예약</h2>
                  <p>{reservationResult?.data.reservationNo ?? "예약 정보 없음"}</p>
                  <small>{reservationResult?.data.holdExpiresAt ?? "좌석 선점 후 주문을 생성할 수 있습니다."}</small>
                </div>
                <div className="action-row">
                  <button
                    className="primary-button"
                    onClick={handleCreateOrder}
                    disabled={!reservationResult || busy}
                  >
                    주문 생성
                  </button>
                  <button className="ghost-button" onClick={handleCancelOrder} disabled={!orderResult || orderResult.data.status !== "CREATED" || busy}>
                    주문 취소
                  </button>
                  <button className="primary-button" onClick={handlePayment} disabled={!orderResult || orderResult.data.status !== "CREATED" || busy}>
                    결제 요청
                  </button>
                </div>
              </div>
              <OrderPanel
                order={orderResult?.data ?? null}
                seats={selectedSeats}
                paymentDeadlineAt={paymentDeadlineAt}
              />
            </section>
          )}

          {activeStep === "payment" && (
            <section className="workspace two-column">
              <div>
                <h1>결제</h1>
                <p>주문 결제 제한 시간 안에 결제를 진행합니다.</p>
                <SourceNotice result={paymentResult} />
                <div className="summary-card">
                  <h2>결제 요청</h2>
                  <dl>
                    <dt>결제 ID</dt><dd>{paymentResult?.data.paymentId ?? "-"}</dd>
                    <dt>금액</dt><dd>{paymentResult ? formatPrice(paymentResult.data.amount) : "-"}</dd>
                    <dt>상태</dt><dd>{paymentResult?.data.status ?? "-"}</dd>
                    <dt>PG 주문 ID</dt><dd>{paymentResult?.data.pgOrderId ?? "-"}</dd>
                    <dt>남은 시간</dt><dd><DeadlineCountdown deadlineAt={paymentDeadlineAt} /></dd>
                  </dl>
                </div>
              </div>
              <div className="flow-box">
                <h2>Toss 연동</h2>
                <p>결제 요청 응답으로 Toss 결제창을 엽니다.</p>
                <button
                  className="primary-button full"
                  onClick={handleOpenTossPayment}
                  disabled={!paymentResult || busy}
                >
                  Toss 결제창 열기
                </button>
                <button className="primary-button full" onClick={handleLoadTickets}>티켓 화면으로 이동</button>
              </div>
            </section>
          )}

          {activeStep === "tickets" && (
            <section className="workspace">
              <div className="section-head">
                <div>
                  <h1>내 티켓</h1>
                  <p>결제 완료 후 발급된 티켓을 확인하는 화면입니다.</p>
                </div>
                <button className="ghost-button" onClick={handleLoadTickets}>티켓 불러오기</button>
              </div>
              <SourceNotice result={ticketsResult} />
              <div className="ticket-list">
                {(ticketsResult?.data ?? []).map((ticket) => (
                  <article className="ticket-card" key={ticket.ticketId}>
                    <div>
                      <span className="status-pill issued">{ticket.status}</span>
                      <h2>{ticket.title}</h2>
                      <p>{ticket.seat}</p>
                      <small>{ticket.gameAt}</small>
                    </div>
                    <div className="qr-box">{ticket.qrToken}</div>
                  </article>
                ))}
              </div>
            </section>
          )}
        </section>
      </main>
    </div>
  );
}

function OrderPanel({
  order,
  seats,
  paymentDeadlineAt
}: {
  order: OrderResponse | null;
  seats: GameSeat[];
  paymentDeadlineAt: number | null;
}) {
  return (
    <aside className="order-panel">
      <h2>주문 상세</h2>
      {!order ? (
        <p>주문 생성 후 상세 정보가 표시됩니다.</p>
      ) : (
        <>
          <dl>
            <dt>주문 번호</dt><dd>{order.orderNo}</dd>
            <dt>상태</dt><dd>{order.status}</dd>
            <dt>남은 시간</dt><dd><DeadlineCountdown deadlineAt={paymentDeadlineAt} /></dd>
            <dt>총 금액</dt><dd>{formatPrice(order.totalAmount)}</dd>
          </dl>
          <div className="order-items">
            {order.orderItems.map((item) => (
              <div key={item.orderItemId}>
                <span>{formatOrderSeat(item.gameSeatId, seats)}</span>
                <strong>{formatPrice(item.price)}</strong>
              </div>
            ))}
          </div>
        </>
      )}
    </aside>
  );
}

function formatOrderSeat(gameSeatId: number, seats: GameSeat[]) {
  const seat = seats.find((candidate) => candidate.gameSeatId === gameSeatId);
  if (!seat) return `좌석 ID ${gameSeatId}`;
  return `${seat.zoneName} ${seat.seatRow}열 ${seat.seatNumber}번`;
}

export default App;
