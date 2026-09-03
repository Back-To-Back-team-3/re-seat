import type {
    CongestionLevel,
    StadiumCongestion,
    StadiumZoneSpot,
    ZoneCategory,
} from "@/types/congestion";

interface ZoneDefinition {
    id: string;
    name: string;
    category: ZoneCategory;
    description: string;
    guideTip: string;
    latitude: number;
    longitude: number;
    levelOffset: number;
    waitTimeByLevel: Record<CongestionLevel, string>;
}

export const STADIUM_ZONE_DEFINITIONS: ZoneDefinition[] = [
    // 1. 경기장 게이트 & 매표소
    {
        id: "central-ticket-booth",
        name: "중앙 매표소 & 무인 발권기",
        category: "출입구/게이트",
        description: "현장 티켓 발권 및 지류 티켓 교환처, 중앙 광장 만남의 장소",
        guideTip: "발권 대기열 발생 가능 · 스마트티켓 사전 발급 시 바로 입장 권장",
        latitude: 37.5118,
        longitude: 127.0718,
        levelOffset: 1,
        waitTimeByLevel: {
            여유: "발권 대기 3~5분",
            보통: "발권 대기 10~15분",
            "약간 붐빔": "발권 대기 20~30분",
            붐빔: "발권 대기 30분 이상 (스마트티켓 권장)",
        },
    },
    {
        id: "gate-1st-base",
        name: "1루 출입구 (홈팀 방면)",
        category: "출입구/게이트",
        description: "1루 내야석 / 오렌지석(응원석) / 블루석 / 테이블석 진입 게이트",
        guideTip: "경기 시작 30분 전 인파 집중 · 소지품 검사 사전 준비 권장",
        latitude: 37.5126,
        longitude: 127.0725,
        levelOffset: 0,
        waitTimeByLevel: {
            여유: "대기 없이 즉시 입장 가능",
            보통: "입장 대기 5~10분",
            "약간 붐빔": "입장 대기 10~20분",
            붐빔: "입장 대기 20분 이상 (외야 우회 권장)",
        },
    },
    {
        id: "gate-3rd-base",
        name: "3루 출입구 (원정팀 방면)",
        category: "출입구/게이트",
        description: "3루 내야석 / 원정 응원석 및 공식 굿즈 팝업스토어 인근",
        guideTip: "원정팀 유니폼/굿즈 구매 대기열과 동선이 겹칠 수 있습니다",
        latitude: 37.5117,
        longitude: 127.0709,
        levelOffset: 0,
        waitTimeByLevel: {
            여유: "대기 없이 즉시 입장 가능",
            보통: "입장 대기 5~10분",
            "약간 붐빔": "입장 대기 10~20분",
            붐빔: "입장 대기 20분 이상",
        },
    },
    {
        id: "gate-outfield",
        name: "외야 출입구 & 백호광장",
        category: "출입구/게이트",
        description: "외야 그린지정석 진입로 및 야외 푸드트럭 먹거리존",
        guideTip: "내야 게이트 대비 대기시간이 가장 짧은 빠른 입장 추천 게이트",
        latitude: 37.5132,
        longitude: 127.0714,
        levelOffset: -1,
        waitTimeByLevel: {
            여유: "대기 없이 즉시 입장 가능",
            보통: "입장 대기 3~5분",
            "약간 붐빔": "입장 대기 5~10분",
            붐빔: "입장 대기 10~15분",
        },
    },

    // 2. 지하철역 & 대중교통
    {
        id: "station-exit-5-6",
        name: "종합운동장역 5·6번 출구 (2·9호선)",
        category: "지하철/대중교통",
        description: "야구장 최단거리 직결 출구, 2·9호선 환승 인파 집중 구간",
        guideTip: "경기 종료 후 9호선 급행 승강장 극심한 혼잡 · 2호선 우회 권장",
        latitude: 37.5115,
        longitude: 127.0728,
        levelOffset: 1,
        waitTimeByLevel: {
            여유: "하차 후 도보 2분 (원활)",
            보통: "하차 후 도보 2분 (보통)",
            "약간 붐빔": "승하차 인파 집중 (도보 5분 소요)",
            붐빔: "승강장 극심한 혼잡 (도보 10분 이상)",
        },
    },
    {
        id: "subway-bongeunsa",
        name: "봉은사역 / 삼성역 방면 (9·2호선)",
        category: "지하철/대중교통",
        description: "탄천 보행교를 건너 코엑스/삼성역으로 이어지는 분산 보행로",
        guideTip: "경기 후 종합운동장역 인파를 피해 쾌적하게 지하철을 탈 수 있는 추천 우회로",
        latitude: 37.5142,
        longitude: 127.0620,
        levelOffset: -1,
        waitTimeByLevel: {
            여유: "도보 약 12~15분 (쾌적)",
            보통: "도보 약 12~15분 소요",
            "약간 붐빔": "도보 약 15~18분 소요",
            붐빔: "도보 약 15~20분 소요",
        },
    },

    // 3. 먹거리 & 주차
    {
        id: "jamsilsaenae-food",
        name: "잠실새내역 먹자골목 (새마을시장)",
        category: "먹거리/주차",
        description: "야구 관람 전 닭강정, 만두 등 인기 먹거리 포장 및 경기 후 뒤풀이 명소",
        guideTip: "경기 시작 2시간 전 방문 시 인기 먹거리 대기 없이 포장 가능",
        latitude: 37.5116,
        longitude: 127.0850,
        levelOffset: 0,
        waitTimeByLevel: {
            여유: "포장 대기 없이 즉시 구매 가능",
            보통: "포장 대기 약 5~10분",
            "약간 붐빔": "인기 매장 포장 대기 15~25분",
            붐빔: "포장 대기 30분 이상 (사전 예약 추천)",
        },
    },
    {
        id: "south-parking",
        name: "탄천 공영주차장 / 남문 출구",
        category: "먹거리/주차",
        description: "탄천 유수지 공영주차장 진출입로 및 대형버스 승하차 구역",
        guideTip: "경기 종료 직후 일시적 병목 발생 · 30분 후 출차 또는 대중교통 권장",
        latitude: 37.5105,
        longitude: 127.0750,
        levelOffset: -1,
        waitTimeByLevel: {
            여유: "입출차 원활 (대기 없음)",
            보통: "출차 소요 약 10~15분",
            "약간 붐빔": "경기 종료 후 출차 20~30분 소요",
            붐빔: "출차 정체 40분 이상 (대중교통 강력 권장)",
        },
    },
];

const LEVEL_ORDER: CongestionLevel[] = ["여유", "보통", "약간 붐빔", "붐빔"];

const LEVEL_MESSAGES: Record<CongestionLevel, string> = {
    여유: "이동과 대기가 매우 원활하여 쾌적하게 이용하실 수 있습니다.",
    보통: "일반적인 수준의 유동인구로 무리 없이 통행이 가능합니다.",
    "약간 붐빔": "인파가 유입되고 있어 입장 및 이용에 대기시간이 소폭 발생할 수 있습니다.",
    붐빔: "관람객 밀집 구간입니다. 인근 다른 출입구나 우회 동선 이용을 적극 권장합니다.",
};

export function adjustLevel(
    baseLevel: CongestionLevel,
    offset: number,
): CongestionLevel {
    const currentIndex = LEVEL_ORDER.indexOf(baseLevel);
    if (currentIndex === -1) return "보통";
    const nextIndex = Math.max(
        0,
        Math.min(LEVEL_ORDER.length - 1, currentIndex + offset),
    );
    return LEVEL_ORDER[nextIndex];
}

export function calculateStadiumZones(
    baseCongestion?: StadiumCongestion | null,
): StadiumZoneSpot[] {
    const baseLevel: CongestionLevel =
        baseCongestion?.congestionLevel ?? "보통";

    return STADIUM_ZONE_DEFINITIONS.map((def) => {
        const spotLevel = adjustLevel(baseLevel, def.levelOffset);

        return {
            id: def.id,
            name: def.name,
            category: def.category,
            description: def.description,
            guideTip: def.guideTip,
            waitTimeEst: def.waitTimeByLevel[spotLevel],
            latitude: def.latitude,
            longitude: def.longitude,
            congestionLevel: spotLevel,
            congestionMessage: LEVEL_MESSAGES[spotLevel],
        };
    });
}
