import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {TicketCard} from "@/components/tickets/ticket-card";

const ticket = {
    ticketId: 1,
    ticketNo: "TICKET-20260912-001",
    gameId: 1,
    seat: "1루 응원석 A열 10번",
    status: "ISSUED" as const,
    qrToken: "QR-20260912-001",
    gameAt: "2026-09-12T18:30:00",
    refundable: true,
    refundDeadline: "2026-09-11T18:30:00",
};

const meta = {
    title: "티켓/TicketCard",
    component: TicketCard,
    decorators: [
        (Story) => (
            <div className="w-[min(900px,100%)] p-6">
                <Story/>
            </div>
        ),
    ],
    args: {
        ticket,
        gameTitle: "LG 트윈스 vs 두산 베어스",
        showRefundAction: true,
        onRequestRefund: () => undefined,
    },
} satisfies Meta<typeof TicketCard>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Issued: Story = {};

export const RefundPending: Story = {
    args: {
        ticket: {...ticket, status: "REFUND_PENDING"},
    },
};

export const RefundFailed: Story = {
    args: {
        ticket: {...ticket, status: "REFUND_FAILED"},
    },
};
