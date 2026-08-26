"use client";

import {useMutation, useQuery, useQueryClient} from "@tanstack/react-query";
import {useEffect, useState, useSyncExternalStore} from "react";

import {
    clearAuth,
    clearAuthNotice,
    consumeAuthCallback,
    getAuthSnapshot,
    getLoginUrl,
    getServerAuthSnapshot,
    parseAuthSnapshot,
    setAuthNotice,
    setVerified,
    subscribeAuth,
} from "@/api/auth";
import {userKeys} from "@/api/query-keys/users";
import {getMyProfile, verifyIdentity} from "@/api/users";

/**
 * 브라우저 인증 상태와 백엔드 사용자 상태를 하나의 화면용 인터페이스로 제공합니다.
 *
 * access token, 본인인증 저장값, 역할, 일회성 알림은 `useSyncExternalStore`로
 * 구독하고, 백엔드가 소유하는 최신 프로필은 TanStack Query로 조회합니다.
 * 본인인증처럼 서버 상태를 변경하는 요청은 Mutation으로 실행한 뒤 프로필을
 * 다시 조회하여 브라우저 저장값을 서버의 최종 결과와 맞춥니다.
 *
 * 서버 렌더링에서는 고정된 비로그인 스냅샷을 사용하고, hydration 이후에만
 * localStorage를 읽으므로 사용자별 토큰이 서버 요청 사이에 공유되지 않습니다.
 *
 * @returns 로그인·프로필·본인인증 상태와 화면에서 호출할 인증 동작
 */
export function useAuth() {
    const queryClient = useQueryClient();

    // React가 외부 인증 저장소의 변경을 구독하고, 환경에 맞는 현재 값을 읽게 한다.
    const snapshot = useSyncExternalStore(
        subscribeAuth,
        getAuthSnapshot,
        getServerAuthSnapshot,
    );
    const session = parseAuthSnapshot(snapshot);

    // Query 오류는 캐시에 남아 있으므로 닫은 메시지를 기억해 같은 오류가 즉시 다시 보이지 않게 한다.
    const [hiddenMessage, setHiddenMessage] = useState<string | null>(null);

    useEffect(() => {
        // OAuth 공급자가 되돌려 보낸 쿼리는 브라우저가 마운트된 뒤 정확히 한 번 소비한다.
        consumeAuthCallback();
    }, []);

    const profileQuery = useQuery({
        queryKey: userKeys.me(),
        queryFn: getMyProfile,
        // 서버 초기 렌더링과 로그아웃 상태에서는 인증이 필요한 요청을 보내지 않는다.
        enabled: session.isAuthed,
    });

    useEffect(() => {
        if (profileQuery.data) {
            // 서버의 최신 인증 여부를 기존 브라우저 키에도 반영해 새로고침 동작을 유지한다.
            setVerified(profileQuery.data.isVerified);
        }
    }, [profileQuery.data]);

    const verification = useMutation({
        mutationFn: verifyIdentity,
        onSuccess: async () => {
            // 인증 API는 변경된 프로필을 반환하지 않으므로 서버의 최종 상태를 다시 조회한다.
            const result = await profileQuery.refetch();
            if (result.data) {
                setVerified(result.data.isVerified);
            }
            setAuthNotice("본인인증이 완료되었습니다.");
        },
    });

    // 사용자가 먼저 확인해야 하는 요청 오류를 성공 알림보다 우선해서 표시한다.
    const currentMessage =
        verification.error?.message ??
        profileQuery.error?.message ??
        session.notice ??
        null;
    const message = currentMessage === hiddenMessage ? null : currentMessage;

    return {
        isAuthed: session.isAuthed,
        isVerified: profileQuery.data?.isVerified ?? session.isVerified,
        profile: profileQuery.data ?? null,
        role: session.role,
        busy: profileQuery.isLoading || verification.isPending,
        message,
        messageVariant:
            verification.error || profileQuery.error
                ? ("error" as const)
                : ("success" as const),
        login() {
            window.location.href = getLoginUrl();
        },
        logout() {
            clearAuth();
            // 다음 로그인 사용자가 직전 사용자의 프로필 캐시를 잠시 보지 않도록 즉시 제거한다.
            queryClient.removeQueries({queryKey: userKeys.me()});
        },
        verify(impUid: string) {
            // PortOne 콜백은 반환값을 기다리지 않으므로 오류 상태를 훅이 관리하는 mutate를 사용한다.
            verification.mutate(impUid);
        },
        dismissMessage() {
            // Query 오류와 메모리 알림을 같은 닫기 동작으로 숨기되 서버 오류 상태 자체는 변경하지 않는다.
            setHiddenMessage(currentMessage);
            clearAuthNotice();
        },
    };
}
