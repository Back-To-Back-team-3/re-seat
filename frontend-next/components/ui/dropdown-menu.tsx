"use client";

import {Menu as MenuPrimitive} from "@base-ui/react/menu";
import type {ComponentProps} from "react";

import {cn} from "@/lib/utils";

const DropdownMenu = MenuPrimitive.Root;
const DropdownMenuTrigger = MenuPrimitive.Trigger;

function DropdownMenuContent({
    className,
    ...props
}: MenuPrimitive.Popup.Props) {
    return (
        <MenuPrimitive.Portal>
            <MenuPrimitive.Positioner align="end" sideOffset={8}>
                <MenuPrimitive.Popup
                    className={cn(
                        "z-50 min-w-48 rounded-control border border-border bg-popover p-1.5 text-popover-foreground shadow-xl outline-none",
                        className,
                    )}
                    {...props}
                />
            </MenuPrimitive.Positioner>
        </MenuPrimitive.Portal>
    );
}

function DropdownMenuItem({
    className,
    ...props
}: MenuPrimitive.Item.Props) {
    return (
        <MenuPrimitive.Item
            className={cn(
                "flex min-h-10 cursor-default items-center gap-2 rounded-control px-3 text-sm font-semibold outline-none data-highlighted:bg-muted",
                className,
            )}
            {...props}
        />
    );
}

function DropdownMenuLinkItem({
    className,
    ...props
}: MenuPrimitive.LinkItem.Props) {
    return (
        <MenuPrimitive.LinkItem
            className={cn(
                "flex min-h-10 items-center gap-2 rounded-control px-3 text-sm font-semibold outline-none data-highlighted:bg-muted",
                className,
            )}
            closeOnClick
            {...props}
        />
    );
}

function DropdownMenuSeparator({
    className,
    ...props
}: ComponentProps<"div">) {
    return <div className={cn("my-1 h-px bg-border", className)} {...props}/>;
}

export {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuLinkItem,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
};
