import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {PaymentScreen} from "@/components/payments/payment-screen";
import {storyGames, storyOrder, storyPayment} from "@/story/fixtures";

const meta = {
    title: "페이지/결제",
    component: PaymentScreen,
    parameters: {layout: "fullscreen"},
    decorators: [
        (Story) => (
            <div className="p-6">
                <Story/>
            </div>
        ),
    ],
    args: {
        game: storyGames[0],
        order: storyOrder,
        payment: storyPayment,
        busy: false,
        error: null,
        onOpenPayment: () => undefined,
        onRefreshOrder: () => undefined,
        onTickets: () => undefined,
        onGames: () => undefined,
        onBack: () => undefined,
    },
} satisfies Meta<typeof PaymentScreen>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Ready: Story = {};

export const Busy: Story = {
    args: {busy: true},
};

export const Approved: Story = {
    args: {
        order: {...storyOrder, status: "PAID"},
        payment: {
            ...storyPayment,
            status: "APPROVED",
            method: "간편결제",
            approvedAt: "2099-09-12 18:02:00",
        },
    },
};

export const Loading: Story = {
    args: {payment: null},
};

export const Error: Story = {
    args: {error: "결제 정보를 확인할 수 없습니다."},
};
