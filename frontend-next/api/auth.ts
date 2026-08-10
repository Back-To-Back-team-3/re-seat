import {storage} from "@/lib/storage";
import type {UserRole} from "@/types/auth";

const BACKEND_ORIGIN =
    process.env.NEXT_PUBLIC_BACKEND_ORIGIN ?? "http://localhost:8080";

const EMPTY_SNAPSHOT = JSON.stringify([null, null, null, null]);

let notice: string | null = null;
const listeners = new Set<() => void>();

function storeAuthTokens(accessToken: string, refreshToken: string) {
    storage.local.set("accessToken", accessToken);
    storage.local.set("refreshToken", refreshToken);
}

function removeAuthValues() {
    storage.local.remove("accessToken");
    storage.local.remove("refreshToken");
    storage.local.remove("queueToken");
    storage.local.remove("isVerified");
}

function emitAuthChange() {
    listeners.forEach((listener) => listener());
}

/**
 * 브라우저 인증 저장소의 변경을 React에 전달할 구독을 등록합니다.
 *
 * 현재 탭에서 발생한 로그인·로그아웃은 `emitAuthChange()`가 알리고,
 * 다른 탭에서 변경된 localStorage는 브라우저의 `storage` 이벤트가 알립니다.
 * 반환 함수는 컴포넌트가 사라질 때 두 구독을 모두 정리하는 데 사용됩니다.
 *
 * @param listener 인증 상태를 다시 읽어야 할 때 호출할 함수
 * @returns 등록한 구독과 이벤트 리스너를 제거하는 함수
 */
export function subscribeAuth(listener: () => void) {
    listeners.add(listener);
    window.addEventListener("storage", listener);

    return () => {
        listeners.delete(listener);
        window.removeEventListener("storage", listener);
    };
}

/**
 * useSyncExternalStore가 값 변경 여부를 비교할 수 있도록 인증 저장소를 문자열로 만든다.
 *
 * 원시 문자열은 같은 내용일 때 참조가 바뀌지 않으므로 불필요한 재렌더링을 만들지 않는다.
 */
export function getAuthSnapshot() {
    return JSON.stringify([
        storage.local.get("accessToken"),
        storage.local.get("isVerified"),
        notice,
        getAccessTokenRole(),
    ]);
}

/**
 * 서버 렌더링에는 브라우저별 인증 정보가 없다는 고정 스냅샷을 제공한다.
 *
 * 서버 모듈에 사용자 토큰을 저장하지 않으므로 서로 다른 요청의 인증 정보가 섞이지 않는다.
 */
export function getServerAuthSnapshot() {
    return EMPTY_SNAPSHOT;
}

/**
 * 문자열 스냅샷을 화면에서 사용하기 쉬운 인증 상태로 변환합니다.
 *
 * localStorage에는 boolean을 직접 저장할 수 없으므로 `"true"`일 때만
 * 본인인증 완료로 판단합니다. 서버 초기 스냅샷처럼 토큰과 역할이 없으면
 * 비로그인 상태와 `role=null`로 해석합니다.
 *
 * @param snapshot `getAuthSnapshot()` 또는 `getServerAuthSnapshot()`의 반환값
 * @returns 로그인·본인인증·알림·역할을 포함한 인증 상태
 */
export function parseAuthSnapshot(snapshot: string) {
    const [accessToken, verified, currentNotice, role] = JSON.parse(snapshot) as [
            string | null,
            string | null,
            string | null,
            UserRole | null,
    ];

    return {
        isAuthed: Boolean(accessToken),
        isVerified: verified === "true",
        notice: currentNotice,
        role,
    };
}

/**
 * OAuth 성공 콜백의 기존 쿼리 이름을 읽고 브라우저 인증 저장소로 옮긴다.
 *
 * 두 토큰이 모두 있을 때만 로그인 성공으로 처리한다. 저장 후에는 주소창에서
 * 토큰 쿼리를 즉시 지워 사용자가 URL을 복사하거나 새로고침할 때 노출되지 않게 한다.
 *
 * @returns 완전한 OAuth 토큰 쌍을 저장했으면 `true`, 아니면 `false`
 */
export function consumeAuthCallback() {
    const params = new URLSearchParams(window.location.search);
    const accessToken = params.get("accessToken");
    const refreshToken = params.get("refreshToken");

    if (!accessToken || !refreshToken) return false;

    storeAuthTokens(accessToken, refreshToken);
    storage.local.set(
        "isVerified",
        String(params.get("isVerified") === "true"),
    );
    notice = "카카오 로그인에 성공했습니다.";

    window.history.replaceState(
        {},
        "",
        window.location.origin + window.location.pathname,
    );
    emitAuthChange();
    return true;
}

/**
 * 현재 사용자의 인증 및 예매 대기열 식별 정보를 브라우저에서 제거합니다.
 *
 * 다른 계정으로 다시 로그인했을 때 이전 사용자의 대기열 토큰이나 본인인증
 * 상태가 이어지지 않도록 인증 토큰과 함께 `queueToken`, `isVerified`도 지웁니다.
 */
export function clearAuth() {
    removeAuthValues();
    notice = "로그아웃했습니다.";
    emitAuthChange();
}

/**
 * 토큰 재발급으로 받은 새 인증 토큰을 저장하고 구독자에게 알립니다.
 *
 * 같은 탭에서는 localStorage의 `storage` 이벤트가 발생하지 않으므로,
 * 토큰을 직접 저장하는 대신 이 함수를 사용해야 역할 등 파생 상태도 갱신됩니다.
 *
 * @param accessToken 새로 발급받은 access token
 * @param refreshToken 함께 교체할 refresh token
 */
export function setAuthTokens(accessToken: string, refreshToken: string) {
    storeAuthTokens(accessToken, refreshToken);
    emitAuthChange();
}

/**
 * refresh token으로도 인증을 복구하지 못한 세션을 만료 처리합니다.
 *
 * 수동 로그아웃과 동일한 인증값을 제거하지만, 사용자 동작이 아닌 인증 만료임을
 * 구분해 화면에는 별도의 안내 메시지를 제공합니다.
 */
export function expireAuth() {
    removeAuthValues();
    notice = "로그인이 만료되었습니다.";
    emitAuthChange();
}

/**
 * 서버에서 확인한 최신 본인인증 여부를 브라우저 저장소와 동기화합니다.
 *
 * 값이 실제로 달라졌을 때만 구독자에게 알려 불필요한 재렌더링을 방지합니다.
 *
 * @param isVerified 서버 프로필이 반환한 본인인증 완료 여부
 */
export function setVerified(isVerified: boolean) {
    if (storage.local.get("isVerified") === String(isVerified)) return;

    storage.local.set("isVerified", String(isVerified));
    emitAuthChange();
}

/**
 * 로그인이나 본인인증 성공처럼 새로고침 후 유지할 필요가 없는 알림을 설정합니다.
 *
 * 알림은 localStorage가 아닌 모듈 메모리에만 두므로 페이지를 새로고침하면 사라집니다.
 *
 * @param message 화면에 표시할 인증 관련 알림
 */
export function setAuthNotice(message: string) {
    notice = message;
    emitAuthChange();
}

/**
 * 현재 인증 알림을 제거하고 화면에 변경을 알립니다.
 */
export function clearAuthNotice() {
    notice = null;
    emitAuthChange();
}

/**
 * Spring Security의 카카오 OAuth 로그인 시작 주소를 반환합니다.
 *
 * 이 경로는 `/api/v1` 아래의 REST API가 아니므로 API 기준 주소가 아닌
 * 백엔드 origin을 사용합니다.
 */
export function getLoginUrl() {
    return `${BACKEND_ORIGIN}/oauth2/authorization/kakao`;
}

/**
 * access token의 JWT payload에서 화면 표시용 사용자 역할을 읽습니다.
 *
 * Base64URL 형식을 브라우저의 `atob()`가 읽을 수 있는 Base64로 변환한 뒤
 * `userRole` claim을 확인합니다. 여기서는 토큰 서명을 검증하지 않으므로
 * 메뉴 표시 등에만 사용하며, 실제 접근 권한은 반드시 백엔드가 검증해야 합니다.
 * 토큰이 없거나 손상된 경우에는 역할을 판단할 수 없으므로 `null`을 반환합니다.
 *
 * @returns 토큰에 명시된 역할 또는 역할을 확인할 수 없을 때 `null`
 */
export function getAccessTokenRole(): UserRole | null {
    const accessToken = storage.local.get("accessToken");
    if (!accessToken) return null;

    try {
        const payload = accessToken.split(".")[1];
        const normalized = payload.replace(/-/g, "+").replace(/_/g, "/");
        const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
        const claims = JSON.parse(atob(padded)) as { userRole?: string };
        return claims.userRole === "ADMIN" ? "ADMIN" : "USER";
    } catch {
        return null;
    }
}
