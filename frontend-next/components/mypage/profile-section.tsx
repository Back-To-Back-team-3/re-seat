"use client";

import {LogOut, TriangleAlert, UserRoundX} from "lucide-react";
import {useState} from "react";

import {Badge} from "@/components/ui/badge";
import {Button} from "@/components/ui/button";
import {
    Dialog,
    DialogClose,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
    DialogTrigger,
} from "@/components/ui/dialog";
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
                                    <Badge>관리자</Badge>
                                )}
                                <Badge
                                    variant={isVerified ? "success" : "warning"}
                                >
                                    {isVerified ? "본인인증 완료" : "본인 미인증"}
                                </Badge>
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
                        <Button
                            className="h-10"
                            onClick={onLogout}
                            size="sm"
                            type="button"
                            variant="outline"
                        >
                            <LogOut aria-hidden="true"/>
                            로그아웃
                        </Button>
                        <Dialog
                            onOpenChange={setShowWithdrawModal}
                            open={showWithdrawModal}
                        >
                            <DialogTrigger
                                render={
                                    <Button
                                        className="h-10"
                                        size="sm"
                                        type="button"
                                        variant="destructive"
                                    />
                                }
                            >
                                <UserRoundX aria-hidden="true"/>
                                회원 탈퇴
                            </DialogTrigger>
                            <DialogContent showCloseButton={!isWithdrawing}>
                                <DialogHeader>
                                    <TriangleAlert
                                        aria-hidden="true"
                                        className="size-6 text-destructive"
                                    />
                                    <DialogTitle className="text-destructive">
                                        회원 탈퇴 안내
                                    </DialogTitle>
                                    <DialogDescription>
                                        정말로 탈퇴하시겠습니까?
                                        <br/>
                                        회원 탈퇴 시 보유 중인{" "}
                                        <strong className="text-foreground">
                                            모바일 티켓, 예매 내역 및 계정 정보가 모두 삭제
                                        </strong>
                                        되며 즉시 로그아웃됩니다.
                                    </DialogDescription>
                                </DialogHeader>
                                <DialogFooter>
                                    <DialogClose
                                        disabled={isWithdrawing}
                                        render={
                                            <Button
                                                size="sm"
                                                type="button"
                                                variant="outline"
                                            />
                                        }
                                    >
                                        취소
                                    </DialogClose>
                                    <Button
                                        loading={isWithdrawing}
                                        onClick={onWithdraw}
                                        size="sm"
                                        type="button"
                                        variant="destructive"
                                    >
                                        {isWithdrawing ? "탈퇴 처리 중..." : "탈퇴 확인"}
                                    </Button>
                                </DialogFooter>
                            </DialogContent>
                        </Dialog>
                    </div>
                </div>
            </div>
        </section>
    );
}
