export const paymentKeys = {
  all: ["payments"] as const,
  detail: (paymentId: number) =>
    [...paymentKeys.all, "detail", paymentId] as const,
};
