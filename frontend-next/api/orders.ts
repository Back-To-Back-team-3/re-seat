import {apiRequest, unwrap} from "@/api/client";
import type {ApiResponse} from "@/types/api";
import type {OrderResponse} from "@/types/order";

export async function createOrder(reservationId: number) {
    const response = await apiRequest<ApiResponse<OrderResponse>>("/orders", {
        method: "POST",
        body: JSON.stringify({
            reservationId,
            discountCode: "",
            deliveryType: "MOBILE",
        }),
    });
    return unwrap(response);
}

export async function getOrder(orderId: number) {
    const response = await apiRequest<ApiResponse<OrderResponse>>(
        `/orders/${orderId}`,
    );
    return unwrap(response);
}

export async function cancelOrder(orderId: number) {
    const response = await apiRequest<
        ApiResponse<{ orderId: number; status: OrderResponse["status"] }>
    >(`/orders/${orderId}/cancel`, {method: "POST"});
    return unwrap(response);
}
