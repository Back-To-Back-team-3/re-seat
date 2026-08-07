import { Alert } from "@/components/common/alert";
import { formatPrice } from "@/lib/currency";
import type { PaymentResponse } from "@/types/payment";

export function PaymentScreen({
  error,
  payment,
  onPay,
}: {
  error: string | null;
  payment: PaymentResponse | null;
  onPay: () => void;
}) {
  if (error) return <Alert message={error} variant="error" />;
  if (!payment) return <p>결제 정보를 불러오고 있습니다.</p>;
  return (
    <section className="grid gap-5">
      <h1 className="text-3xl font-black">결제</h1>
      <div className="rounded-panel border border-border bg-surface p-6 shadow-card">
        <span className="text-brand">{payment.status}</span>
        <strong className="mt-2 block text-2xl">
          {formatPrice(payment.amount)}
        </strong>
      </div>
      {payment.status === "READY" && (
        <button
          className="rounded-control bg-brand px-5 py-3 font-bold text-white"
          onClick={onPay}
          type="button"
        >
          Toss로 결제하기
        </button>
      )}
    </section>
  );
}
