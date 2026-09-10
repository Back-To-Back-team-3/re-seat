import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {CheckoutScreen} from "@/components/orders/checkout-screen";
import {
    storyGames,
    storyOrder,
    storyReservation,
    storySeats,
} from "@/story/fixtures";

const meta = {
    title: "페이지/주문 확인",
    parameters: {layout: "fullscreen"},
    decorators: [
        (Story) => (
            <div className="p-6">
                <Story/>
            </div>
        ),
    ],
} satisfies Meta<typeof CheckoutScreen>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Reservation: Story = {
    render: () => (
        <CheckoutScreen
            busy={false}
            game={storyGames[0]}
            onBack={() => undefined}
            onCreateOrder={() => undefined}
            order={null}
            reservation={storyReservation}
            seats={storySeats.slice(0, 2)}
        />
    ),
};

export const CreatedOrder: Story = {
    render: () => (
        <CheckoutScreen
            busy={false}
            game={storyGames[0]}
            onBack={() => undefined}
            onCancelOrder={() => undefined}
            onPayment={() => undefined}
            onRefreshOrder={() => undefined}
            order={storyOrder}
            seats={storySeats.slice(0, 2)}
        />
    ),
};

export const Busy: Story = {
    render: () => (
        <CheckoutScreen
            busy
            game={storyGames[0]}
            onBack={() => undefined}
            onCancelOrder={() => undefined}
            onPayment={() => undefined}
            onRefreshOrder={() => undefined}
            order={storyOrder}
            seats={storySeats.slice(0, 2)}
        />
    ),
};

export const Empty: Story = {
    render: () => (
        <CheckoutScreen
            busy={false}
            game={null}
            onBack={() => undefined}
            onCreateOrder={() => undefined}
            order={null}
            reservation={null}
            seats={[]}
        />
    ),
};
