export type OrderStatus = "CREATED" | "PAID" | "CANCELED" | "EXPIRED";

export type OrderResponse = {
    orderId: number;
    orderNo: string;
    totalAmount: number;
    status: OrderStatus;
    paymentDeadline: string;
    holdExpiresAt: string;
    orderItems: Array<{
        orderItemId: number;
        gameSeatId: number;
        price: number;
    }>;
};
