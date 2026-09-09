export type VerificationAgency = "PASS" | "TOSS" | "KFTC";

const VERIFICATION_AGENCIES = [
    {
        id: "PASS" as const,
        name: "PASS",
        badge: "통신사",
    },
    {
        id: "TOSS" as const,
        name: "토스",
        badge: "간편인증",
    },
    {
        id: "KFTC" as const,
        name: "금융인증서",
        badge: "은행",
    },
] as const;

type VerificationAgencySelectorProps = {
    value: VerificationAgency;
    onChange: (agency: VerificationAgency) => void;
};

/** 본인인증에 사용할 인증 기관을 하나 선택하게 한다. */
export function VerificationAgencySelector({
    value,
    onChange,
}: VerificationAgencySelectorProps) {
    return (
        <div className="mt-4 mb-1">
            <span className="mb-2 block text-[11px] font-bold text-foreground">
                인증 수단 선택
            </span>
            <div className="grid grid-cols-3 gap-2">
                {VERIFICATION_AGENCIES.map((agency) => {
                    const selected = value === agency.id;

                    return (
                        <button
                            aria-pressed={selected}
                            className={[
                                "flex cursor-pointer flex-col items-center justify-center rounded-control border p-2.5 text-center transition duration-fast",
                                selected
                                    ? "border-brand bg-brand/8 font-bold text-brand shadow-xs ring-1 ring-brand"
                                    : "border-border bg-surface text-muted-foreground hover:border-foreground/30 hover:bg-surface-soft",
                            ].join(" ")}
                            key={agency.id}
                            onClick={() => onChange(agency.id)}
                            type="button"
                        >
                            <span className="text-[12px] font-extrabold">
                                {agency.name}
                            </span>
                            <span className="mt-0.5 text-[9px] opacity-75">
                                {agency.badge}
                            </span>
                        </button>
                    );
                })}
            </div>
        </div>
    );
}
