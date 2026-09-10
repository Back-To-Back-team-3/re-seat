const VERIFICATION_STEPS = [
    {
        number: "1",
        title: "카카오 로그인",
        description: "계정 확인 완료",
        active: true,
    },
    {
        number: "2",
        title: "휴대폰 본인인증",
        description: "현재 단계",
        active: true,
    },
    {
        number: "3",
        title: "경기 예매 시작",
        description: "인증 후 바로 이용",
        active: false,
    },
] as const;

/** 로그인부터 예매 시작까지의 본인인증 진행 단계를 표시한다. */
export function VerificationSteps() {
    return (
        <ol className="mt-8 grid list-none gap-4 p-0">
            {VERIFICATION_STEPS.map((step, index) => (
                <li
                    className={[
                        "flex items-center gap-3",
                        step.active ? "opacity-100" : "opacity-45",
                    ].join(" ")}
                    key={step.number}
                >
                    <span
                        className={[
                            "grid size-[29px] shrink-0 place-items-center rounded-full border font-mono text-[9px]",
                            index === 1
                                ? "border-brand bg-brand"
                                : "border-white/35",
                        ].join(" ")}
                    >
                        {step.number}
                    </span>
                    <div className="grid gap-0.5">
                        <strong className="text-[11px]">{step.title}</strong>
                        <small className="text-[8px] opacity-70">
                            {step.description}
                        </small>
                    </div>
                </li>
            ))}
        </ol>
    );
}
