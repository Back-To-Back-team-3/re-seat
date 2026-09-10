import {Badge} from "@/components/ui/badge";
import type {AdminUser} from "@/types/admin";

const statusLabel = {ACTIVE: "활성", SUSPENDED: "정지", DELETED: "탈퇴"};

export function AdminUserList({
    users,
    onSelect,
}: {
    users: AdminUser[];
    onSelect: (userId: number) => void;
}) {
    if (users.length === 0) {
        return <p className="py-16 text-center text-sm text-muted-foreground">검색된 회원이 없습니다.</p>;
    }

    return (
        <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] border-collapse text-left text-sm">
                <thead className="border-b border-border bg-muted/50 text-xs text-muted-foreground">
                    <tr>
                        <th className="px-4 py-3 font-bold">이름</th>
                        <th className="px-4 py-3 font-bold">이메일</th>
                        <th className="px-4 py-3 font-bold">권한</th>
                        <th className="px-4 py-3 font-bold">상태</th>
                        <th className="px-4 py-3 font-bold">본인인증</th>
                        <th className="px-4 py-3 font-bold">가입일</th>
                    </tr>
                </thead>
                <tbody className="divide-y divide-border">
                    {users.map((user) => (
                        <tr className="hover:bg-muted/40" key={user.id}>
                            <td className="px-4 py-3 font-bold">{user.name ?? "-"}</td>
                            <td className="px-4 py-3">
                                <button
                                    aria-label={`회원 상세 보기 ${user.email}`}
                                    className="font-medium text-primary hover:underline"
                                    onClick={() => onSelect(user.id)}
                                    type="button"
                                >
                                    {user.email}
                                </button>
                            </td>
                            <td className="px-4 py-3">{user.role}</td>
                            <td className="px-4 py-3">
                                <Badge variant={user.status === "ACTIVE" ? "success" : user.status === "SUSPENDED" ? "warning" : "secondary"}>
                                    {statusLabel[user.status]}
                                </Badge>
                            </td>
                            <td className="px-4 py-3">{user.isVerified ? "완료" : "미완료"}</td>
                            <td className="px-4 py-3 text-muted-foreground">{user.createdAt.slice(0, 10)}</td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}
