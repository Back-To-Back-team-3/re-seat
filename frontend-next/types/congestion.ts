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
