import {http, HttpResponse} from "msw";
import {describe, expect, it} from "vitest";

import {API_BASE_URL} from "@/api/client";
import {updateMyProfile} from "@/api/users";
import {server} from "@/test/mocks/server";

describe("사용자 API", () => {
    it("이름과 전화번호를 회원정보 수정 API에 전달한다", async () => {
        server.use(
            http.put(`${API_BASE_URL}/users/me`, async ({request}) => {
                expect(await request.json()).toEqual({
                    name: "새 이름",
                    phone: "010-9876-5432",
                });

                return HttpResponse.json({
                    success: true,
                    errorCode: null,
                    message: "회원정보 수정 완료",
                    data: null,
                });
            }),
        );

        await expect(
            updateMyProfile({
                name: "새 이름",
                phone: "010-9876-5432",
            }),
        ).resolves.toBeUndefined();
    });
});
