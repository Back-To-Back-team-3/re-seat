import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {BookingSeatSummary} from "@/components/seats/booking-seat-summary";
import {calculateTotalPrice} from "@/lib/currency";
import {storyReservation, storySeats} from "@/story/fixtures";

const selectedSeats = storySeats.slice(0, 2);

const meta = {
    title: "좌석/BookingSeatSummary",
    component: BookingSeatSummary,
    decorators: [
        (Story) => (
            <div className="w-[min(380px,100%)] p-6">
                <Story/>
            </div>
        ),
    ],
    args: {
        seats: selectedSeats,
        total: calculateTotalPrice(selectedSeats),
        busy: false,
        locked: false,
        timerTarget: "2099-09-12 18:10:00",
        timerExpired: false,
        onTimerExpire: () => undefined,
        onCancelReservation: () => undefined,
        onContinue: () => undefined,
        onReserve: () => undefined,
    },
} satisfies Meta<typeof BookingSeatSummary>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Selecting: Story = {};

export const Reserved: Story = {
    args: {reservation: storyReservation, locked: true},
};

export const Empty: Story = {
    args: {seats: [], total: 0},
};

export const Expired: Story = {
    args: {timerExpired: true},
};
