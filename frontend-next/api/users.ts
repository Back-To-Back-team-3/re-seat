import {apiRequest, unwrap} from "@/api/client";
import type {ApiResponse} from "@/types/api";
import type {UserProfile} from "@/types/auth";

/**
 * 백엔드의 사용자 프로필 응답을 API 경계에서 수용하기 위한 타입입니다.
 *
 * Java DTO의 필드명은 `isVerified`지만, boolean getter인 `isVerified()`를
 * Jackson이 JavaBeans 규칙에 따라 `verified`로 직렬화할 수 있습니다.
 * OAuth 콜백과 프론트 내부 상태는 `isVerified`를 사용하므로, 이 파일에서만
 * 두 응답 이름을 허용하고 `UserProfile.isVerified` 하나로 정규화합니다.
 */
type UserProfilePayload = Omit<UserProfile, "isVerified"> & {
    isVerified?: boolean;
    verified?: boolean;
};

/**
 * 로그인한 사용자의 최신 프로필을 조회합니다.
 *
 * 공통 응답에서 `data`를 꺼낸 뒤 본인인증 필드 이름을 `isVerified`로
 * 통일합니다. 현재 이름과 구버전 이름이 모두 없다면 미인증으로 처리하여
 * 인증하지 않은 사용자가 예매 화면으로 진입하지 않게 합니다.
 *
 * @returns 프론트에서 사용하는 표준 사용자 프로필
 * @throws API 요청이 실패하거나 응답에 `data`가 없으면 `AppError`
 */
export async function getMyProfile() {
    const response =
        await apiRequest<ApiResponse<UserProfilePayload>>("/users/me");
    const {isVerified, verified, ...profile} = unwrap(response);

    return {
        ...profile,
        // 배포된 백엔드 응답의 구버전 필드도 기존 Vite와 동일하게 허용한다.
        isVerified: isVerified ?? verified ?? false,
    } satisfies UserProfile;
}

/**
 * PortOne 본인인증 결과를 백엔드에 전달합니다.
 *
 * `impUid`는 인증 성공 여부를 프론트가 직접 판단하기 위한 값이 아니라,
 * 백엔드가 PortOne에 실제 인증 결과를 확인할 때 사용하는 식별자입니다.
 * 이 API는 변경된 프로필을 반환하지 않으므로 호출이 성공한 뒤
 * `getMyProfile()`을 다시 조회해 서버의 최신 `isVerified`를 반영해야 합니다.
 *
 * @param impUid PortOne 인증 성공 콜백에서 받은 인증 식별자
 * @throws 인증 검증 또는 API 요청이 실패하면 `AppError`
 */
export async function verifyIdentity(impUid: string) {
    await apiRequest<ApiResponse<void>>("/users/verification", {
        method: "POST",
        body: JSON.stringify({impUid}),
    });
}
