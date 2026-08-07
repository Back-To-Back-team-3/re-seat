import { formatPrice } from "@/lib/currency";
import type { OrderResponse } from "@/types/order";

export function OrderSummary({ order }: { order: OrderResponse }) {
  return (
    <section className="grid gap-3 rounded-panel border border-border bg-surface p-6 shadow-card">
      <span className="text-sm font-bold text-brand">{order.status}</span>
      <strong className="text-2xl">{order.orderNo}</strong>
      <span>모바일 티켓 · {order.orderItems.length}석</span>
      <strong>{formatPrice(order.totalAmount)}</strong>
    </section>
  );
}
