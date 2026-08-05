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
