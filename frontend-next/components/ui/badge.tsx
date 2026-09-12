import {mergeProps} from "@base-ui/react/merge-props";
import {useRender} from "@base-ui/react/use-render";
import {cva, type VariantProps} from "class-variance-authority";

import {cn} from "@/lib/utils";

const badgeVariants = cva(
    "inline-flex h-5 w-fit shrink-0 items-center justify-center gap-1 overflow-hidden rounded-full border border-transparent px-2 py-0.5 text-xs font-bold whitespace-nowrap focus-visible:ring-3 focus-visible:ring-ring/35 [&>svg]:pointer-events-none [&>svg]:size-3",
    {
        variants: {
            variant: {
                default: "bg-primary text-primary-foreground",
                secondary: "bg-secondary text-secondary-foreground",
                success: "bg-success/10 text-success",
                warning: "bg-highlight/15 text-warning-foreground",
                destructive: "bg-destructive/10 text-destructive",
                outline: "border-border text-foreground",
            },
        },
        defaultVariants: {
            variant: "default",
        },
    },
);

function Badge({
    className,
    render,
    variant = "default",
    ...props
}: useRender.ComponentProps<"span"> & VariantProps<typeof badgeVariants>) {
    return useRender({
        defaultTagName: "span",
        props: mergeProps<"span">(
            {className: cn(badgeVariants({variant}), className)},
            props,
        ),
        render,
        state: {
            slot: "badge",
            variant,
        },
    });
}

export {Badge, badgeVariants};
