type TossRequest = {
  amount: number;
  orderId: string;
  orderName: string;
  customerName: string;
  customerEmail?: string;
  successUrl: string;
  failUrl: string;
};

export async function openTossPayment(request: TossRequest) {
  const clientKey = process.env.NEXT_PUBLIC_TOSS_CLIENT_KEY;
  if (!clientKey) {
    throw new Error("Toss 결제 클라이언트 키를 확인해주세요.");
  }
  const browserWindow = window as typeof window & {
    TossPayments?: (key: string) => {
      requestPayment: (method: "카드", request: TossRequest) => Promise<void>;
    };
  };

  if (!browserWindow.TossPayments) {
    await new Promise<void>((resolve, reject) => {
      const existing = document.querySelector<HTMLScriptElement>(
        'script[data-reseat-toss="true"]',
      );
      if (existing) {
        existing.addEventListener("load", () => resolve(), { once: true });
        existing.addEventListener(
          "error",
          () => reject(new Error("Toss 결제 SDK를 불러오지 못했습니다.")),
          { once: true },
        );
        return;
      }
      const script = document.createElement("script");
      script.src = "https://js.tosspayments.com/v1/payment";
      script.dataset.reseatToss = "true";
      script.onload = () => resolve();
      script.onerror = () =>
        reject(new Error("Toss 결제 SDK를 불러오지 못했습니다."));
      document.head.appendChild(script);
    });
  }

  const tossFactory = (
    window as typeof window & {
      TossPayments?: (key: string) => {
        requestPayment: (method: "카드", request: TossRequest) => Promise<void>;
      };
    }
  ).TossPayments;
  if (!tossFactory) {
    throw new Error("Toss 결제 SDK 설정을 확인해주세요.");
  }
  await tossFactory(clientKey).requestPayment("카드", request);
}
