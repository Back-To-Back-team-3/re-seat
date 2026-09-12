import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {SeatMap} from "@/components/seats/seat-map";
import {storySeats} from "@/story/fixtures";

const meta = {
    title: "좌석/SeatMap",
    component: SeatMap,
    decorators: [
        (Story) => (
            <div className="w-[min(820px,100%)] p-6">
                <Story/>
            </div>
        ),
    ],
    args: {
        seats: storySeats,
        selectedSeats: storySeats.slice(0, 2),
        selectedZoneName: "1루 A",
        locked: false,
        onToggle: () => undefined,
    },
} satisfies Meta<typeof SeatMap>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Selected: Story = {};

export const Empty: Story = {
    args: {seats: [], selectedSeats: [], selectedZoneName: null},
};

export const Locked: Story = {
    args: {locked: true},
};
