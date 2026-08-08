import { COMPLETED_GAME_IDS_KEY } from "@/lib/constants";
import { storage } from "@/lib/storage";

/**
 * 이미 결제를 완료한 경기 ID 집합을 로컬 저장소에서 읽어온다.
 *
 * 기존 Vite 화면과 같은 키를 사용해 마이그레이션 전후의 완료 상태를 공유한다.
 */
export function getCompletedGameIds(): Set<number> {
  const stored = storage.local.getJson<number[]>(COMPLETED_GAME_IDS_KEY);
  return new Set(Array.isArray(stored) ? stored : []);
}

/**
 * 결제가 완료된 경기 ID를 브라우저에 기록한다.
 *
 * Set으로 기존 값을 합치므로 결제 콜백이나 상태 재조회가 여러 번 실행되어도
 * 같은 경기 ID가 중복 저장되지 않는다.
 */
export function rememberCompletedGame(gameId?: number | null): void {
  if (!gameId) return;

  const completedGameIds = getCompletedGameIds();
  completedGameIds.add(gameId);
  storage.local.set(
    COMPLETED_GAME_IDS_KEY,
    JSON.stringify([...completedGameIds]),
  );
}
