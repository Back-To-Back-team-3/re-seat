/**
 * 모든 화면 아래에 브랜드 소개와 저작권 정보를 표시한다.
 */
export function Footer() {
    return (
        <footer
            className="grid grid-cols-[1fr_auto] items-end gap-10 border-t border-border bg-surface px-[7vw] py-12 max-sm:grid-cols-1 max-sm:px-6 max-sm:py-[38px]">
            <div className="grid content-start gap-2">
                <strong className="font-brand text-[21px]">
                    Re:<span className="text-brand">Seat</span>
                </strong>
                <p className="m-0 text-[9px] leading-[1.7] text-muted-foreground">
                    KBO 리그 예매 서비스.
                    <br/>
                    최고의 경험을 최고의 자리에서.
                </p>
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
