export type CongestionLevel = "여유" | "보통" | "약간 붐빔" | "붐빔";

export interface StadiumCongestion {
    stadiumNum: number;
    stadiumName: string;
    areaName: string;
    congestionLevel: CongestionLevel;
    congestionMessage: string;
    populationMin: number | null;
    populationMax: number | null;
    latitude: number;
    longitude: number;
    observedAt: string;
}

export type ZoneCategory = "출입구/게이트" | "지하철/대중교통" | "먹거리/주차";

export interface StadiumZoneSpot {
    id: string;
    name: string;
    category: ZoneCategory;
    description: string;
    guideTip: string;
    waitTimeEst: string;
    latitude: number;
    longitude: number;
    congestionLevel: CongestionLevel;
    congestionMessage: string;
}
