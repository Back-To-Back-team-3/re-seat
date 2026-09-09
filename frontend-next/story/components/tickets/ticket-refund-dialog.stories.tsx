import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {TicketRefundDialog} from "@/components/tickets/ticket-refund-dialog";

const ticket = {
    ticketId: 1,
    ticketNo: "TICKET-20260912-001",
    gameId: 1,
    seat: "1루 응원석 A열 10번",
    status: "ISSUED" as const,
    qrToken: "QR-20260912-001",
    gameAt: "2026-09-12T18:30:00",
};

const meta = {
    title: "티켓/TicketRefundDialog",
    component: TicketRefundDialog,
    args: {
        ticket,
        errorMessage: null,
        pending: false,
        onClose: () => undefined,
        onConfirm: () => undefined,
    },
} satisfies Meta<typeof TicketRefundDialog>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Request: Story = {};

export const Retry: Story = {
    args: {
        ticket: {...ticket, status: "REFUND_FAILED"},
    },
};

export const FailedRequest: Story = {
    args: {
        errorMessage: "환불 요청을 처리하지 못했습니다.",
    },
};
