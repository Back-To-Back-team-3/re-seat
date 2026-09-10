"use client";

import {TriangleAlert, UserRoundX} from "lucide-react";
import {type FormEvent, useState} from "react";

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
import type {UserProfile} from "@/types/auth";

interface AccountManagementProps {
    profile: UserProfile;
    onUpdate: (request: {name: string; phone: string}) => Promise<void> | void;
    onWithdraw: () => void;
    isUpdating: boolean;
    isWithdrawing: boolean;
}

const inputClassName =
    "h-11 w-full rounded-control border border-border bg-background px-3 text-sm outline-none transition-colors placeholder:text-muted-foreground focus:border-primary focus:ring-3 focus:ring-ring/20 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground";

export function AccountManagement({
    profile,
    onUpdate,
    onWithdraw,
    isUpdating,
    isWithdrawing,
}: AccountManagementProps) {
    const [name, setName] = useState(profile.name ?? "");
    const [phone, setPhone] = useState(profile.phone ?? "");
    const [showWithdrawModal, setShowWithdrawModal] = useState(false);

    const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();

        try {
            await onUpdate({name: name.trim(), phone: phone.trim()});
        } catch {
            // 요청 오류는 마이페이지 상단의 공통 알림에서 안내한다.
        }
    };

    return (
        <section
            aria-labelledby="mypage-account-tab"
            className="p-5 sm:p-8 md:p-10"
            id="mypage-account-panel"
            role="tabpanel"
        >
            <header className="border-b border-border pb-6">
                <div className="flex flex-wrap items-center gap-2">
                    <h2 className="text-2xl font-black">회원정보 관리</h2>
                    <Badge variant={profile.isVerified ? "success" : "warning"}>
                        {profile.isVerified ? "본인인증 완료" : "본인 미인증"}
                    </Badge>
                </div>
                <p className="mt-2 text-sm text-muted-foreground">
                    예매 안내에 사용되는 이름과 연락처를 관리합니다.
                </p>
            </header>

            <form className="grid gap-6 py-8" onSubmit={handleSubmit}>
                <div className="grid gap-2">
                    <label className="text-sm font-bold" htmlFor="profile-email">
                        이메일
                    </label>
                    <input
                        className={inputClassName}
                        disabled
                        id="profile-email"
                        type="email"
                        value={profile.email}
                    />
                    <p className="text-xs text-muted-foreground">
                        카카오 계정 이메일은 이곳에서 변경할 수 없습니다.
                    </p>
                </div>

                <div className="grid gap-2 sm:grid-cols-2 sm:gap-5">
                    <div className="grid gap-2">
                        <label className="text-sm font-bold" htmlFor="profile-name">
                            이름
                        </label>
                        <input
                            autoComplete="name"
                            className={inputClassName}
                            id="profile-name"
                            maxLength={50}
                            onChange={(event) => setName(event.target.value)}
                            required
                            value={name}
                        />
                    </div>
                    <div className="grid gap-2">
                        <label className="text-sm font-bold" htmlFor="profile-phone">
                            전화번호
                        </label>
                        <input
                            autoComplete="tel"
                            className={inputClassName}
                            id="profile-phone"
                            onChange={(event) => setPhone(event.target.value)}
                            pattern="\d{2,3}-\d{3,4}-\d{4}"
                            placeholder="010-1234-5678"
                            required
                            type="tel"
                            value={phone}
                        />
                    </div>
                </div>

                <div className="flex justify-end">
                    <Button loading={isUpdating} type="submit">
                        변경사항 저장
                    </Button>
                </div>
            </form>

            <section className="border-t border-border pt-8">
                <h3 className="font-bold text-destructive">회원 탈퇴</h3>
                <div className="mt-3 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                    <p className="max-w-xl text-sm leading-relaxed text-muted-foreground">
                        탈퇴하면 계정 정보가 삭제되며 되돌릴 수 없습니다. 정산이 끝나지
                        않은 티켓이 있으면 탈퇴할 수 없습니다.
                    </p>
                    <Dialog
                        onOpenChange={setShowWithdrawModal}
                        open={showWithdrawModal}
                    >
                        <DialogTrigger
                            render={
                                <Button
                                    className="self-start sm:self-auto"
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
                                    회원 탈퇴 시 계정 정보가 삭제되며 되돌릴 수 없습니다.
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
            </section>
        </section>
    );
}
