"use client";

import {useEffect, useRef, useState} from "react";
import type {UserProfile, UserRole} from "@/types/auth";

interface ProfileSectionProps {
    profile: UserProfile | null;
    role: UserRole | null;
    isVerified: boolean;
    onLogout: () => void;
    onWithdraw: () => void;
    isWithdrawing?: boolean;
}

export function ProfileSection({
    profile,
    role,
    isVerified,
    onLogout,
    onWithdraw,
    isWithdrawing = false,
}: ProfileSectionProps) {
    const [showWithdrawModal, setShowWithdrawModal] = useState(false);
    const triggerRef = useRef<HTMLButtonElement>(null);
    const dialogRef = useRef<HTMLDivElement>(null);
    const cancelButtonRef = useRef<HTMLButtonElement>(null);

    const closeModal = () => {
        setShowWithdrawModal(false);
        // 모달을 연 트리거 버튼으로 포커스 복원
        triggerRef.current?.focus();
    };

    useEffect(() => {
        if (!showWithdrawModal) return;

        // 모달이 열리면 첫 조작 요소(취소 버튼)로 포커스 이동
        cancelButtonRef.current?.focus();

        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === "Escape") {
                event.preventDefault();
                closeModal();
                return;
            }

            if (event.key === "Tab" && dialogRef.current) {
                const focusableElements = dialogRef.current.querySelectorAll<HTMLElement>(
                    'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
                );
                if (focusableElements.length === 0) return;

                const firstElement = focusableElements[0];
                const lastElement = focusableElements[focusableElements.length - 1];

                if (event.shiftKey) {
                    if (document.activeElement === firstElement) {
                        event.preventDefault();
                        lastElement.focus();
                    }
                } else {
                    if (document.activeElement === lastElement) {
                        event.preventDefault();
                        firstElement.focus();
                    }
                }
            }
        };

        window.addEventListener("keydown", handleKeyDown);
        return () => {
            window.removeEventListener("keydown", handleKeyDown);
        };
    }, [showWithdrawModal]);

    return (
        <section className="mx-auto mb-10 w-full max-w-[1120px]">
            <div className="rounded-[16px] border border-border bg-surface p-6 shadow-sm md:p-8">
                <div className="flex flex-col gap-6 sm:flex-row sm:items-center sm:justify-between">
                    <div className="flex items-center gap-4">
                        <div className="grid size-14 place-items-center rounded-full bg-brand/10 text-2xl font-black text-brand">
                            {(profile?.name ?? profile?.nickname ?? "U")[0]?.toUpperCase()}
                        </div>
                        <div>
                            <div className="flex items-center gap-2">
                                <h2 className="text-xl font-bold tracking-tight">
                                    {profile?.name ?? profile?.nickname ?? "사용자"}
                                </h2>
                                {role === "ADMIN" && (
                                    <span className="rounded-full bg-brand px-2 py-0.5 text-[11px] font-bold text-white">
                                        관리자
                                    </span>
                                )}
                                <span
                                    className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${
                                        isVerified
                                            ? "bg-success/10 text-success"
                                            : "bg-amber-500/10 text-amber-600 dark:text-amber-400"
                                    }`}
                                >
                                    {isVerified ? "본인인증 완료" : "본인 미인증"}
                                </span>
                            </div>
                            <p className="mt-1 text-sm text-muted-foreground">
                                {profile?.email ?? "이메일 정보 없음"}
                            </p>
                            {profile?.phone && (
                                <p className="text-xs text-muted-foreground">
                                    연락처: {profile.phone}
                                </p>
                            )}
                        </div>
                    </div>

                    <div className="flex items-center gap-2.5">
                        <button
                            className="inline-flex min-h-10 items-center justify-center rounded-control border border-border bg-surface px-4 text-xs font-bold text-foreground transition hover:bg-muted"
                            onClick={onLogout}
                            type="button"
                        >
                            로그아웃
                        </button>
                        <button
                            className="inline-flex min-h-10 items-center justify-center rounded-control border border-destructive/40 bg-destructive/5 px-4 text-xs font-bold text-destructive transition hover:bg-destructive hover:text-white"
                            onClick={() => setShowWithdrawModal(true)}
                            ref={triggerRef}
                            type="button"
                        >
                            회원 탈퇴
                        </button>
                    </div>
                </div>
            </div>

            {/* 회원 탈퇴 확인 모달 */}
            {showWithdrawModal && (
                <div
                    aria-labelledby="withdraw-dialog-title"
                    aria-modal="true"
                    className="fixed inset-0 z-50 grid place-items-center bg-black/60 p-4 backdrop-blur-sm"
                    ref={dialogRef}
                    role="dialog"
                >
                    <div className="w-full max-w-md rounded-[16px] border border-border bg-surface p-6 shadow-2xl">
                        <div className="mb-4">
                            <span className="inline-block text-2xl">⚠️</span>
                            <h3
                                className="mt-2 text-lg font-bold text-destructive"
                                id="withdraw-dialog-title"
                            >
                                회원 탈퇴 안내
                            </h3>
                            <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                                정말로 탈퇴하시겠습니까?
                                <br />
                                회원 탈퇴 시 보유 중인 <strong className="text-foreground">모바일 티켓, 예매 내역 및 계정 정보가 모두 삭제</strong>되며 즉시 로그아웃됩니다.
                            </p>
                        </div>
                        <div className="flex justify-end gap-3 pt-2">
                            <button
                                className="inline-flex min-h-10 items-center justify-center rounded-control border border-border bg-surface px-4 text-xs font-bold text-muted-foreground transition hover:text-foreground"
                                disabled={isWithdrawing}
                                onClick={closeModal}
                                ref={cancelButtonRef}
                                type="button"
                            >
                                취소
                            </button>
                            <button
                                className="inline-flex min-h-10 items-center justify-center rounded-control bg-destructive px-4 text-xs font-bold text-white transition hover:bg-destructive/90 disabled:opacity-50"
                                disabled={isWithdrawing}
                                onClick={() => {
                                    onWithdraw();
                                }}
                                type="button"
                            >
                                {isWithdrawing ? "탈퇴 처리 중..." : "탈퇴 확인"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </section>
    );
}
