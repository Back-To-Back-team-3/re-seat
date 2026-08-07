import { KST_OFFSET, KST_TIME_ZONE } from "@/lib/constants";

/**
 * 백엔드가 전달한 경기 일시를 JavaScript Date로 변환한다.
 *
 * 백엔드의 LocalDateTime 문자열에는 시간대 정보가 없지만 실제 기준은
 * Asia/Seoul이다. 브라우저의 로컬 시간대로 해석하면 사용자 환경에 따라
 * 경기 시간이 달라지므로, 오프셋이 없는 값에만 KST 오프셋을 덧붙인다.
 */
export function parseApiDateTime(value?: string | null): Date | null {
  if (!value) return null;

  let normalized = value.trim().replace(" ", "T");
  normalized = normalized.replace(/\.(\d{3})\d+/, ".$1");

  if (!/(?:Z|[+-]\d{2}:?\d{2})$/i.test(normalized)) {
    normalized = normalized.includes("T")
      ? `${normalized}${KST_OFFSET}`
      : `${normalized}T00:00:00${KST_OFFSET}`;
  }

  const parsed = new Date(normalized);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

/**
 * 제한시간이 이미 지났는지 판단한다.
 *
 * Countdown은 1초 간격 타이머로 만료를 알리므로 첫 렌더링에는 아직 알림이 없다.
 * 새로고침으로 다시 진입한 화면은 그 사이에 기한이 지났는지 즉시 알아야 하므로
 * 같은 기준을 이 함수로 공유한다. 기한 값 자체가 없으면 제한이 없는 것이므로
 * 만료로 보지 않는다.
 */
export function isDeadlineExpired(value?: string | null): boolean {
  const target = parseApiDateTime(value);

  if (!target) return false;

  // Countdown과 같은 올림 기준을 사용해 표시와 판정이 어긋나지 않게 한다.
  return Math.ceil((target.getTime() - Date.now()) / 1_000) <= 0;
}

export function formatGameDate(value: string) {
  const date = parseApiDateTime(value);

  // 파싱 실패 시 빈 문구로 숨기지 않고 백엔드 원문을 보여주는 기존 동작을 유지한다.
  if (!date) return value;

  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: KST_TIME_ZONE,
    month: "long",
    day: "numeric",
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
}

/**
 * 경기 카드의 날짜 블록(월/일/요일)을 별도 줄로 표시하기 위한 값을 만든다.
 *
 * 파싱에 실패해도 카드 레이아웃이 깨지지 않도록 자리표시자 문자열을 반환한다.
 */
export function formatShortDate(value: string) {
  const date = parseApiDateTime(value);

  if (!date) return { month: "--", day: "--", weekday: "---" };

  const parts = new Intl.DateTimeFormat("ko-KR", {
    timeZone: KST_TIME_ZONE,
    month: "numeric",
    day: "2-digit",
    weekday: "short",
  }).formatToParts(date);
  const part = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((entry) => entry.type === type)?.value ?? "";

  return {
    month: `${part("month")}월`,
    day: part("day"),
    weekday: part("weekday"),
  };
}
