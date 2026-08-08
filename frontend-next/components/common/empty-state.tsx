export type EmptyStateProps = {
  title: string;
  description: string;
};

/**
 * 조회 결과가 없을 때 제목과 다음 행동을 이해할 수 있는 설명을 표시한다.
 */
export function EmptyState({ title, description }: EmptyStateProps) {
  return (
    <div className="grid min-h-60 place-items-center content-center gap-2 rounded-[14px] border border-dashed border-border bg-surface p-[30px] text-center">
      <span aria-hidden="true" className="text-[38px] text-brand">
        ○
      </span>
      <strong className="text-base">{title}</strong>
      <p className="m-0 max-w-[460px] text-[13px] leading-[1.6] text-muted-foreground">
        {description}
      </p>
    </div>
  );
}
