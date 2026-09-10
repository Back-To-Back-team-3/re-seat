import {useState} from "react";
import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {SeatMap} from "@/components/seats/seat-map";
import {SeatSummary} from "@/components/seats/seat-summary";
import {storyReservation, storySeats} from "@/story/fixtures";
import type {GameSeat} from "@/types/game";

const meta = {
    title: "페이지/좌석 선택",
    parameters: {layout: "fullscreen"},
} satisfies Meta;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
    render: () => {
        const [selectedSeats, setSelectedSeats] = useState<GameSeat[]>(
            storySeats.slice(0, 1),
        );

        function toggleSeat(seat: GameSeat) {
            setSelectedSeats((current) =>
                current.some((item) => item.gameSeatId === seat.gameSeatId)
                    ? current.filter((item) => item.gameSeatId !== seat.gameSeatId)
                    : [...current, seat].slice(0, 2),
            );
        }

        return (
            <main className="mx-auto grid max-w-[1120px] grid-cols-[minmax(0,1fr)_340px] gap-4 p-6 max-[1024px]:grid-cols-1">
                <SeatMap
                    locked={false}
                    onToggle={toggleSeat}
                    seats={storySeats}
                    selectedSeats={selectedSeats}
                    selectedZoneName="1루 A"
                />
                <SeatSummary
                    busy={false}
                    locked={false}
                    onCancelReservation={() => undefined}
                    onContinue={() => undefined}
                    onReserve={() => undefined}
                    reservation={storyReservation}
                    seats={selectedSeats}
                    timerTarget={storyReservation.holdExpiresAt}
                />
            </main>
        );
    },
};
