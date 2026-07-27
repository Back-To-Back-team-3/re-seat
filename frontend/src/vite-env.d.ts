/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_USE_MOCK_FALLBACK?: string;
  readonly VITE_TOSS_CLIENT_KEY?: string;
  readonly VITE_PORTONE_CODE?: string;
}

interface Window {
  TossPayments?: (clientKey: string) => {
    requestPayment: (
      method: string,
      options: {
        amount: number;
        orderId: string;
        orderName: string;
        customerName?: string;
        customerEmail?: string;
        successUrl: string;
        failUrl: string;
      }
    ) => Promise<void>;
  };
}
