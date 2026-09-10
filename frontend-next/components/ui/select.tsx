"use client";

import {Select as SelectPrimitive} from "@base-ui/react/select";
import {Check, ChevronDown, ChevronUp} from "lucide-react";
import type {ComponentProps} from "react";

import {cn} from "@/lib/utils";

const Select = SelectPrimitive.Root;

function SelectGroup({className, ...props}: SelectPrimitive.Group.Props) {
    return <SelectPrimitive.Group className={cn("scroll-my-1 p-1", className)} data-slot="select-group" {...props}/>;
}

function SelectValue({className, ...props}: SelectPrimitive.Value.Props) {
    return <SelectPrimitive.Value className={cn("flex flex-1 text-left", className)} data-slot="select-value" {...props}/>;
}

function SelectTrigger({
    children,
    className,
    size = "default",
    ...props
}: SelectPrimitive.Trigger.Props & {size?: "sm" | "default"}) {
    return (
        <SelectPrimitive.Trigger
            className={cn(
                "flex w-fit items-center justify-between gap-2 rounded-control border border-input bg-surface px-3 text-sm text-foreground transition-colors outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/35 disabled:cursor-not-allowed disabled:opacity-50 data-placeholder:text-muted-foreground data-[size=default]:h-10 data-[size=sm]:h-9 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
                className,
            )}
            data-size={size}
            data-slot="select-trigger"
            {...props}
        >
            {children}
            <SelectPrimitive.Icon render={<ChevronDown aria-hidden="true" className="text-muted-foreground"/>}/>
        </SelectPrimitive.Trigger>
    );
}

function SelectContent({
    align = "center",
    alignItemWithTrigger = true,
    alignOffset = 0,
    children,
    className,
    side = "bottom",
    sideOffset = 4,
    ...props
}: SelectPrimitive.Popup.Props & Pick<SelectPrimitive.Positioner.Props, "align" | "alignOffset" | "side" | "sideOffset" | "alignItemWithTrigger">) {
    return (
        <SelectPrimitive.Portal>
            <SelectPrimitive.Positioner
                align={align}
                alignItemWithTrigger={alignItemWithTrigger}
                alignOffset={alignOffset}
                className="isolate z-50"
                side={side}
                sideOffset={sideOffset}
            >
                <SelectPrimitive.Popup
                    className={cn(
                        "relative isolate z-50 max-h-(--available-height) min-w-36 origin-(--transform-origin) overflow-x-hidden overflow-y-auto rounded-control border border-border bg-popover text-popover-foreground shadow-card duration-100 data-[align-trigger=true]:w-(--anchor-width) data-[side=bottom]:slide-in-from-top-2 data-[side=left]:slide-in-from-right-2 data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2 data-closed:animate-out data-closed:fade-out-0 data-closed:zoom-out-95 data-open:animate-in data-open:fade-in-0 data-open:zoom-in-95",
                        className,
                    )}
                    data-align-trigger={alignItemWithTrigger}
                    data-slot="select-content"
                    {...props}
                >
                    <SelectScrollUpButton/>
                    <SelectPrimitive.List>{children}</SelectPrimitive.List>
                    <SelectScrollDownButton/>
                </SelectPrimitive.Popup>
            </SelectPrimitive.Positioner>
        </SelectPrimitive.Portal>
    );
}

function SelectLabel({className, ...props}: SelectPrimitive.GroupLabel.Props) {
    return (
        <SelectPrimitive.GroupLabel
            className={cn("px-2 py-1.5 text-xs font-bold text-muted-foreground", className)}
            data-slot="select-label"
            {...props}
        />
    );
}

function SelectItem({children, className, ...props}: SelectPrimitive.Item.Props) {
    return (
        <SelectPrimitive.Item
            className={cn(
                "relative flex w-full cursor-default items-center rounded-md py-2 pr-8 pl-2 text-sm outline-hidden select-none focus:bg-accent focus:text-accent-foreground data-disabled:pointer-events-none data-disabled:opacity-50",
                className,
            )}
            data-slot="select-item"
            {...props}
        >
            <SelectPrimitive.ItemText className="flex flex-1 whitespace-nowrap">{children}</SelectPrimitive.ItemText>
            <SelectPrimitive.ItemIndicator
                render={<span className="pointer-events-none absolute right-2 flex size-4 items-center justify-center"/>}
            >
                <Check aria-hidden="true" className="size-4"/>
            </SelectPrimitive.ItemIndicator>
        </SelectPrimitive.Item>
    );
}

function SelectSeparator({className, ...props}: SelectPrimitive.Separator.Props) {
    return <SelectPrimitive.Separator className={cn("pointer-events-none -mx-1 my-1 h-px bg-border", className)} data-slot="select-separator" {...props}/>;
}

function SelectScrollUpButton({className, ...props}: ComponentProps<typeof SelectPrimitive.ScrollUpArrow>) {
    return (
        <SelectPrimitive.ScrollUpArrow
            className={cn("sticky top-0 z-10 flex w-full cursor-default items-center justify-center bg-popover py-1", className)}
            data-slot="select-scroll-up-button"
            {...props}
        >
            <ChevronUp aria-hidden="true" className="size-4"/>
        </SelectPrimitive.ScrollUpArrow>
    );
}

function SelectScrollDownButton({className, ...props}: ComponentProps<typeof SelectPrimitive.ScrollDownArrow>) {
    return (
        <SelectPrimitive.ScrollDownArrow
            className={cn("sticky bottom-0 z-10 flex w-full cursor-default items-center justify-center bg-popover py-1", className)}
            data-slot="select-scroll-down-button"
            {...props}
        >
            <ChevronDown aria-hidden="true" className="size-4"/>
        </SelectPrimitive.ScrollDownArrow>
    );
}

export {
    Select,
    SelectContent,
    SelectGroup,
    SelectItem,
    SelectLabel,
    SelectScrollDownButton,
    SelectScrollUpButton,
    SelectSeparator,
    SelectTrigger,
    SelectValue,
};
