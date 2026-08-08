/**
 * 모든 화면 아래에 고정으로 나타나는 브랜드, 지원, 정책, 저작권 영역이다.
 *
 * Vite와 같은 3단(desktop) → 2단(900px 이하) → 1단(640px 이하) 배치를 따른다.
 */
export function Footer() {
  return (
    <footer className="grid grid-cols-[2fr_1fr_1fr_1.3fr] gap-10 border-t border-border bg-surface px-[7vw] py-12 max-[900px]:grid-cols-2 max-sm:grid-cols-1 max-sm:px-6 max-sm:py-[38px]">
      <div className="grid content-start gap-2">
        <strong className="font-brand text-[21px]">
          Re:<span className="text-brand">Seat</span>
        </strong>
        <p className="m-0 text-[9px] leading-[1.7] text-muted-foreground">
          KBO 리그 공식 예매 파트너.
          <br />
          최고의 경험을 최고의 자리에서.
        </p>
      </div>
      <div className="grid content-start gap-2">
        <span className="mb-[5px] font-mono text-[8px] leading-[1.7] font-black tracking-[1.4px] text-muted-foreground">
          SUPPORT
        </span>
        <small className="m-0 text-[9px] leading-[1.7] text-muted-foreground">
          이용 안내
        </small>
        <small className="m-0 text-[9px] leading-[1.7] text-muted-foreground">
          고객 센터
        </small>
      </div>
      <div className="grid content-start gap-2">
        <span className="mb-[5px] font-mono text-[8px] leading-[1.7] font-black tracking-[1.4px] text-muted-foreground">
          POLICY
        </span>
        <small className="m-0 text-[9px] leading-[1.7] text-muted-foreground">
          이용 약관
        </small>
        <small className="m-0 text-[9px] leading-[1.7] text-muted-foreground">
          개인정보 처리방침
        </small>
      </div>
      <p className="m-0 self-end text-right text-[9px] leading-[1.7] text-muted-foreground max-sm:text-left">
        © 2026 Re:Seat. All rights reserved. ·{" "}
        <a
          className="underline"
          href="https://commons.wikimedia.org/wiki/File:Doosan_Bears_vs_LG_Twins_(1).jpg"
          rel="noreferrer"
          target="_blank"
        >
          구장 사진: Christophe95, CC BY-SA 4.0
        </a>
      </p>
    </footer>
  );
}
