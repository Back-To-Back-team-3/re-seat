import {describe, expect, it} from "vitest";

import {gameKeys} from "@/api/query-keys/games";
import {orderKeys} from "@/api/query-keys/orders";
import {paymentKeys} from "@/api/query-keys/payments";
import {ticketKeys} from "@/api/query-keys/tickets";
import {userKeys} from "@/api/query-keys/users";

describe("query key factories", () => {
    it("같은 조회 조건에는 구조적으로 동일한 key를 만든다", () => {
        // Query 객체가 달라도 값이 같으면 TanStack Query가 같은 캐시로 인식해야 한다.
        const first = gameKeys.list({bookingStatus: "OPEN", page: 0});
        const second = gameKeys.list({bookingStatus: "OPEN", page: 0});

        expect(first).toEqual(["games", "list", {bookingStatus: "OPEN", page: 0}]);
        expect(second).toEqual(first);

        // 페이지가 다르면 별도 조회 결과이므로 같은 캐시 key를 사용하면 안 된다.
        expect(gameKeys.list({bookingStatus: "OPEN", page: 1})).not.toEqual(first);
    });

    it("도메인과 상세 ID가 드러나는 key를 만든다", () => {
        // 상세 조회와 무효화 코드가 같은 규칙을 공유하도록 결과 구조를 고정한다.
        expect(gameKeys.detail(1)).toEqual(["games", "detail", 1]);
        expect(orderKeys.detail(2)).toEqual(["orders", "detail", 2]);
        expect(paymentKeys.detail(3)).toEqual(["payments", "detail", 3]);
        expect(ticketKeys.list()).toEqual(["tickets", "list"]);
        expect(userKeys.me()).toEqual(["users", "me"]);
    });
});
