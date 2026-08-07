import { COMPLETED_GAME_IDS_KEY } from "@/lib/constants";
import { storage } from "@/lib/storage";

/**
 * 이미 결제를 완료한 경기 ID 집합을 로컬 저장소에서 읽어온다.
 *
 * 결제 완료 시점에 이 키를 기록하는 쓰기 로직(Vite의 rememberCompletedGame)은
 * 아직 Next로 이전되지 않았다. 그래도 Vite와 같은 저장소 키를 미리 참조해두면,
 * 그 기능이 이전된 뒤에도 홈 히어로의 "예매 완료" 표시가 별도 수정 없이
 * 정상 동작한다.
 */
export function getCompletedGameIds(): Set<number> {
  const stored = storage.local.getJson<number[]>(COMPLETED_GAME_IDS_KEY);
  return new Set(Array.isArray(stored) ? stored : []);
}
