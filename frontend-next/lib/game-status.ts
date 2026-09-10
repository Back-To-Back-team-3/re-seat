import type {GameSummary} from "@/types/game";

/** 경기 상태별 화면 문구를 한곳에서 관리한다. */
export const GAME_STATUS_META: Record<
    GameSummary["bookingStatus"],
    {label: string; action: string; description: string}
> = {
    SCHEDULED: {
        label: "예매 예정",
        action: "예매 준비 중",
        description: "예매 오픈 전입니다.",
    },
    OPEN: {
        label: "예매중",
        action: "경기 선택",
        description: "지금 예매할 수 있습니다.",
    },
    CLOSED: {
        label: "예매 종료",
        action: "예매 종료",
        description: "예매가 마감되었습니다.",
    },
    CANCELLED: {
        label: "경기 취소",
        action: "경기 취소",
        description: "취소된 경기입니다.",
    },
};

/** 경기 카드와 오늘 경기 패널이 공유하는 상태별 배지 색상이다. */
export const GAME_STATUS_BADGE_CLASSES: Record<
    GameSummary["bookingStatus"],
    string
> = {
    SCHEDULED: "bg-[rgba(43,103,203,0.1)] text-[#2b67cb]",
    OPEN: "bg-success/10 text-success",
    CLOSED: "bg-[rgba(91,97,112,0.12)] text-muted-foreground",
    CANCELLED: "bg-brand/10 text-brand",
};
