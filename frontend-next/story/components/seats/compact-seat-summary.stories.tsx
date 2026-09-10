import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {CompactSeatSummary} from "@/components/seats/compact-seat-summary";
import {calculateTotalPrice} from "@/lib/currency";
import {storySeats} from "@/story/fixtures";

const selectedSeats = storySeats.slice(0, 2);

const meta = {
    title: "좌석/CompactSeatSummary",
    component: CompactSeatSummary,
    decorators: [
        (Story) => (
            <div className="w-[min(360px,100%)] p-6">
                <Story/>
            </div>
        ),
    ],
    args: {
        seats: selectedSeats,
        total: calculateTotalPrice(selectedSeats),
        busy: false,
        locked: false,
        onReserve: () => undefined,
    },
} satisfies Meta<typeof CompactSeatSummary>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Empty: Story = {
    args: {seats: [], total: 0},
};

export const Locked: Story = {
    args: {locked: true},
};
