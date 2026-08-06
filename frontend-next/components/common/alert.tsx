export type AlertProps = {
  message: string;
  variant: "success" | "error";
  onClose?: () => void;
};

/**
 * API 성공 또는 오류 메시지를 화면 상단에 표시한다.
 *
 * 메시지의 출처는 알지 않고 표시와 닫기 동작만 담당하므로,
 * 이후 인증·예매·결제 화면에서 같은 모양으로 재사용할 수 있다.
 */
export function Alert({ message, variant, onClose }: AlertProps) {
  const isError = variant === "error";

  return (
    <div
      className={[
        "fixed top-[82px] left-1/2 z-80 grid w-[min(520px,calc(100%_-_32px))] -translate-x-1/2",
        "grid-cols-[26px_1fr_auto] items-center gap-2.5 rounded-[10px] border bg-surface px-[15px] py-[13px] shadow-card",
        isError ? "border-brand/30" : "border-success/25",
      ].join(" ")}
      role="status"
    >
      <span
        aria-hidden="true"
        className={[
          "grid size-[26px] place-items-center rounded-full text-xs font-black text-white",
          isError ? "bg-brand" : "bg-success",
        ].join(" ")}
      >
        {isError ? "!" : "✓"}
      </span>
      <p className="m-0 text-[13px] leading-[1.45]">{message}</p>
      {onClose && (
        <button
          aria-label="알림 닫기"
          className="cursor-pointer border-0 bg-transparent text-xl text-muted-foreground"
          onClick={onClose}
          type="button"
        >
          ×
        </button>
      )}
    </div>
  );
}
