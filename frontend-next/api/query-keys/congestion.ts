export const congestionKeys = {
    all: ["congestion"] as const,
    stadiums: () => [...congestionKeys.all, "stadiums"] as const,
    stadium: (stadiumNum: number) =>
        [...congestionKeys.stadiums(), stadiumNum] as const,
};
