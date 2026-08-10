export type PaymentStatus = "READY" | "APPROVED" | "FAILED" | "CANCELED";

export type PaymentCreateResponse = {
    paymentId: number;
    paymentNo: string;
    orderId: number;
    amount: number;
    method: string | null;
    status: PaymentStatus;
    pgProvider: "MOCK" | "TOSS" | "KAKAO" | "NAVER";
    pgOrderId: string;
};

export type PaymentActionResponse = {
    paymentId: number;
    status: PaymentStatus;
};

export type PaymentResponse = Omit<PaymentCreateResponse, "pgOrderId"> & {
    failReason: string | null;
    approvedAt: string | null;
    failedAt: string | null;
};
