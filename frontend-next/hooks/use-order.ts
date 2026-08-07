"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { createOrder, getOrder, cancelOrder } from "@/api/orders";
import { orderKeys } from "@/api/query-keys/orders";
import { useBookingStore } from "@/providers/booking-store-provider";

export function useOrder(orderId?: number) {
  const queryClient = useQueryClient();
  const setOrderId = useBookingStore((state) => state.setOrderId);
  const detail = useQuery({
    queryKey: orderKeys.detail(orderId ?? 0),
    queryFn: () => getOrder(orderId!),
    enabled: Boolean(orderId),
  });
  const create = useMutation({
    mutationFn: createOrder,
    onSuccess: (order) => {
      setOrderId(order.orderId);
      queryClient.setQueryData(orderKeys.detail(order.orderId), order);
    },
  });
  const cancel = useMutation({
    mutationFn: cancelOrder,
    onSuccess: async () => {
      if (orderId) {
        await queryClient.invalidateQueries({
          queryKey: orderKeys.detail(orderId),
        });
      }
    },
  });
  return { detail, create, cancel };
}
