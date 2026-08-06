export const orderKeys = {
  all: ["orders"] as const,
  detail: (orderId: number) =>
    [...orderKeys.all, "detail", orderId] as const,
};
