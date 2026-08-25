import type {OrderStatus} from "@/types/order";
import type {TicketSummary} from "@/types/ticket";

export type PaymentStatus = "READY" | "APPROVED" | "FAILED" | "CANCELED";
export type PgProvider = "MOCK" | "TOSS" | "KAKAO" | "NAVER";

export type PaymentCreateResponse = {
    paymentId: number;
    paymentNo: string;
    orderId: number;
    amount: number;
    method: string | null;
    status: PaymentStatus;
    pgProvider: PgProvider;
    pgOrderId: string;
    paymentDeadline: string;
};

export type PaymentCompleteResponse = {
    paymentId: number;
    paymentNo: string;
    status: PaymentStatus;
    method: string | null;
    orderId: number;
    orderStatus: OrderStatus;
    tickets: TicketSummary[];
};

export type PaymentFailResponse = {
    paymentId: number;
    status: PaymentStatus;
};

export type PaymentResponse = {
    paymentId: number;
    paymentNo: string;
    orderId: number;
    amount: number;
    method: string | null;
    status: PaymentStatus;
    pgProvider: PgProvider;
    failReason: string | null;
    approvedAt: string | null;
    failedAt: string | null;
};
