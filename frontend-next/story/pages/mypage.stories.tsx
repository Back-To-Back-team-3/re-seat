import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {ProfileSection} from "@/components/mypage/profile-section";
import {TicketList} from "@/components/tickets/ticket-list";
import {storyGames, storyProfile, storyTicket} from "@/story/fixtures";

const meta = {
    title: "페이지/마이페이지",
    parameters: {layout: "fullscreen"},
} satisfies Meta;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
    render: () => (
        <main className="mx-auto min-h-[650px] w-full max-w-[var(--width-shell)] px-[var(--gutter-desktop)] py-12 max-sm:px-[var(--gutter-mobile)]">
            <div className="mx-auto mb-8 w-full max-w-[1120px]">
                <span className="text-xs font-extrabold tracking-[0.1em] text-brand">
                    MY PAGE
                </span>
                <h1 className="mt-1 text-[clamp(28px,3.5vw,42px)] font-black">
                    마이페이지
                </h1>
                <p className="text-sm text-muted-foreground">
                    계정 정보를 확인하고 보유 티켓 및 환불을 관리합니다.
                </p>
            </div>
            <ProfileSection
                isVerified
                onLogout={() => undefined}
                onWithdraw={() => undefined}
                profile={storyProfile}
                role="USER"
            />
            <TicketList
                games={storyGames}
                onCancelTicket={() => undefined}
                onReload={() => undefined}
                onRetryCancelTicket={() => undefined}
                reloading={false}
                tickets={[storyTicket]}
            />
        </main>
    ),
};
