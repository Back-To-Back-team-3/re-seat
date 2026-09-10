import {Button as ButtonPrimitive} from "@base-ui/react/button";
import {cva, type VariantProps} from "class-variance-authority";
import {LoaderCircle} from "lucide-react";

import {cn} from "@/lib/utils";

const buttonVariants = cva(
    "group/button inline-flex shrink-0 items-center justify-center gap-2 whitespace-nowrap rounded-control border border-transparent text-sm font-bold transition-colors outline-none select-none focus-visible:ring-3 focus-visible:ring-ring/35 active:not-aria-[haspopup]:translate-y-px disabled:pointer-events-none disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
    {
        variants: {
            variant: {
                default: "bg-primary text-primary-foreground shadow-[0_8px_20px_rgb(224_53_53_/_20%)] hover:bg-brand-dark",
                outline: "border-border bg-surface text-foreground hover:border-foreground",
                secondary: "bg-secondary text-secondary-foreground hover:bg-border",
                ghost: "text-foreground hover:bg-muted",
                destructive: "bg-destructive/10 text-destructive hover:bg-destructive/20",
                link: "text-primary underline-offset-4 hover:underline",
            },
            size: {
                default: "h-11 px-5",
                sm: "h-9 px-3 text-xs",
                lg: "h-12 px-6 text-base",
                icon: "size-11",
                "icon-sm": "size-9",
                "icon-lg": "size-12",
            },
        },
        defaultVariants: {
            variant: "default",
            size: "default",
        },
    },
);

type ButtonProps = ButtonPrimitive.Props &
    VariantProps<typeof buttonVariants> & {
        loading?: boolean;
    };

function Button({
    children,
    className,
    disabled,
    loading = false,
    variant = "default",
    size = "default",
    ...props
}: ButtonProps) {
    return (
        <ButtonPrimitive
            aria-busy={loading || undefined}
            className={cn(buttonVariants({variant, size, className}))}
            data-slot="button"
            disabled={disabled || loading}
            {...props}
        >
            {loading && <LoaderCircle aria-hidden="true" className="animate-spin"/>}
            {children}
        </ButtonPrimitive>
    );
}

export {Button, buttonVariants};
