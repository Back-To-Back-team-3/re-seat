"use client";

import {Search} from "lucide-react";
import {type FormEvent, useState} from "react";

import {Button} from "@/components/ui/button";
import type {AdminUserSearchCondition, UserStatus} from "@/types/admin";
import type {UserRole} from "@/types/auth";

const controlClassName =
    "h-10 rounded-control border border-border bg-background px-3 text-sm outline-none focus:border-primary focus:ring-3 focus:ring-ring/20";

export function AdminUserFilters({
    onSearch,
}: {
    onSearch: (condition: AdminUserSearchCondition) => void;
}) {
    const [email, setEmail] = useState("");
    const [name, setName] = useState("");
    const [role, setRole] = useState<UserRole | "">("");
    const [status, setStatus] = useState<UserStatus | "">("");

    const submit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        onSearch({
            email: email.trim() || undefined,
            name: name.trim() || undefined,
            role: role || undefined,
            status: status || undefined,
        });
    };

    return (
        <form className="grid gap-3 border-b border-border pb-5 xl:grid-cols-[1fr_1fr_140px_140px_auto]" onSubmit={submit}>
            <label className="grid gap-1 text-xs font-bold">
                이메일
                <input
                    className={controlClassName}
                    onChange={(event) => setEmail(event.target.value)}
                    placeholder="user@example.com"
                    value={email}
                />
            </label>
            <label className="grid gap-1 text-xs font-bold">
                이름
                <input
                    className={controlClassName}
                    onChange={(event) => setName(event.target.value)}
                    placeholder="회원 이름"
                    value={name}
                />
            </label>
            <label className="grid gap-1 text-xs font-bold">
                권한
                <select className={controlClassName} onChange={(event) => setRole(event.target.value as UserRole | "")} value={role}>
                    <option value="">전체</option>
                    <option value="USER">일반 회원</option>
                    <option value="ADMIN">관리자</option>
                </select>
            </label>
            <label className="grid gap-1 text-xs font-bold">
                상태
                <select className={controlClassName} onChange={(event) => setStatus(event.target.value as UserStatus | "")} value={status}>
                    <option value="">전체</option>
                    <option value="ACTIVE">활성</option>
                    <option value="SUSPENDED">정지</option>
                    <option value="DELETED">탈퇴</option>
                </select>
            </label>
            <Button className="self-end" type="submit">
                <Search aria-hidden="true"/>
                조회
            </Button>
        </form>
    );
}
