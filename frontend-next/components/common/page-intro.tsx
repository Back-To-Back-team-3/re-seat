import type {ReactNode} from "react";

interface PageIntroProps {
    eyebrow: string;
    title: string;
    description: ReactNode;
    headingLevel?: 1 | 2;
}

/** 주요 페이지의 영문 표식, 제목, 설명을 일관된 계층으로 표시한다. */
export function PageIntro({
                              eyebrow,
                              title,
                              description,
                              headingLevel = 1,
                          }: PageIntroProps) {
    const Heading = headingLevel === 2 ? "h2" : "h1";

    return (
        <div>
            <span className="inline-block text-xs font-extrabold tracking-[0.1em] text-brand">
                {eyebrow}
            </span>
            <Heading className="mt-[7px] mb-1.5 text-[clamp(32px,3.5vw,46px)] tracking-[-0.04em]">
                {title}
            </Heading>
            <p className="m-0 text-sm text-muted-foreground">{description}</p>
        </div>
    );
}
