import type {CongestionLevel} from "@/types/congestion";

interface CongestionBadgeProps {
    level: CongestionLevel;
    className?: string;
}

const CONGESTION_CONFIG: Record<
    CongestionLevel,
    {label: string; colorClass: string; dotClass: string}
> = {
    여유: {
        label: "여유",
        colorClass:
            "bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 border-emerald-500/30",
        dotClass: "bg-emerald-500",
    },
    보통: {
        label: "보통",
        colorClass:
            "bg-blue-500/15 text-blue-600 dark:text-blue-400 border-blue-500/30",
        dotClass: "bg-blue-500",
    },
    "약간 붐빔": {
        label: "약간 붐빔",
        colorClass:
            "bg-amber-500/15 text-amber-600 dark:text-amber-400 border-amber-500/30",
        dotClass: "bg-amber-500",
    },
    붐빔: {
        label: "붐빔",
        colorClass: "bg-brand/15 text-brand border-brand/30",
        dotClass: "bg-brand",
    },
};

export function CongestionBadge({
    level,
    className = "",
}: CongestionBadgeProps) {
    const config = CONGESTION_CONFIG[level] ?? CONGESTION_CONFIG["보통"];

    return (
        <span
            className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-extrabold ${config.colorClass} ${className}`}
        >
            <span
                className={`size-1.5 rounded-full ${config.dotClass} animate-pulse`}
            />
            {config.label}
        </span>
    );
}
