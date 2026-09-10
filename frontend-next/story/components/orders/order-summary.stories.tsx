import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {OrderSummary} from "@/components/orders/order-summary";
import {storyGames, storySeats} from "@/story/fixtures";

const meta = {
    title: "주문/OrderSummary",
    component: OrderSummary,
    decorators: [
        (Story) => (
            <div className="w-[min(720px,100%)] p-6">
                <Story/>
            </div>
        ),
    ],
    args: {
        game: storyGames[0],
        seats: storySeats.slice(0, 2),
    },
} satisfies Meta<typeof OrderSummary>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const SingleSeat: Story = {
    args: {seats: storySeats.slice(0, 1)},
};
