"use client";

import {Dialog as DialogPrimitive} from "@base-ui/react/dialog";
import {X} from "lucide-react";
import type {ComponentProps} from "react";

import {Button} from "@/components/ui/button";
import {cn} from "@/lib/utils";

const Dialog = DialogPrimitive.Root;
const DialogTrigger = DialogPrimitive.Trigger;
const DialogClose = DialogPrimitive.Close;

function DialogOverlay({className, ...props}: DialogPrimitive.Backdrop.Props) {
    return (
        <DialogPrimitive.Backdrop
            className={cn(
                "fixed inset-0 z-50 bg-black/60 backdrop-blur-sm duration-100 data-closed:animate-out data-closed:fade-out-0 data-open:animate-in data-open:fade-in-0",
                className,
            )}
            data-slot="dialog-overlay"
            {...props}
        />
    );
}

function DialogContent({
    children,
    className,
    showCloseButton = true,
    ...props
}: DialogPrimitive.Popup.Props & {showCloseButton?: boolean}) {
    return (
        <DialogPrimitive.Portal>
            <DialogOverlay/>
            <DialogPrimitive.Popup
                className={cn(
                    "fixed top-1/2 left-1/2 z-50 grid w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 gap-4 rounded-modal border border-border bg-popover p-6 text-sm text-popover-foreground shadow-2xl outline-none duration-100 data-closed:animate-out data-closed:fade-out-0 data-closed:zoom-out-95 data-open:animate-in data-open:fade-in-0 data-open:zoom-in-95",
                    className,
                )}
                data-slot="dialog-content"
                {...props}
            >
                {children}
                {showCloseButton && (
                    <DialogPrimitive.Close
                        render={
                            <Button
                                aria-label="닫기"
                                className="absolute top-2 right-2"
                                size="icon-sm"
                                variant="ghost"
                            />
                        }
                    >
                        <X aria-hidden="true"/>
                    </DialogPrimitive.Close>
                )}
            </DialogPrimitive.Popup>
        </DialogPrimitive.Portal>
    );
}

function DialogHeader({className, ...props}: ComponentProps<"div">) {
    return (
        <div
            className={cn("flex flex-col gap-2", className)}
            data-slot="dialog-header"
            {...props}
        />
    );
}

function DialogFooter({className, ...props}: ComponentProps<"div">) {
    return (
        <div
            className={cn("flex flex-col-reverse gap-2 sm:flex-row sm:justify-end", className)}
            data-slot="dialog-footer"
            {...props}
        />
    );
}

function DialogTitle({className, ...props}: DialogPrimitive.Title.Props) {
    return (
        <DialogPrimitive.Title
            className={cn("text-lg font-bold", className)}
            data-slot="dialog-title"
            {...props}
        />
    );
}

function DialogDescription({
    className,
    ...props
}: DialogPrimitive.Description.Props) {
    return (
        <DialogPrimitive.Description
            className={cn("text-sm leading-relaxed text-muted-foreground", className)}
            data-slot="dialog-description"
            {...props}
        />
    );
}

export {
    Dialog,
    DialogClose,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogOverlay,
    DialogTitle,
    DialogTrigger,
};
