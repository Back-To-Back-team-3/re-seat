import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {SeatSummary} from "@/components/seats/seat-summary";
import {storyReservation, storySeats} from "@/story/fixtures";

const meta = {
    title: "좌석/SeatSummary",
    component: SeatSummary,
    decorators: [
        (Story) => (
            <div className="w-[min(380px,100%)] p-6">
                <Story/>
            </div>
        ),
    ],
    args: {
        seats: storySeats.slice(0, 2),
        busy: false,
        locked: false,
        onReserve: () => undefined,
    },
} satisfies Meta<typeof SeatSummary>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Compact: Story = {};

export const BookingFlow: Story = {
    args: {
        reservation: storyReservation,
        timerTarget: storyReservation.holdExpiresAt,
        onContinue: () => undefined,
        onCancelReservation: () => undefined,
    },
};
