import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {PageIntro} from "@/components/common/page-intro";

const meta = {
    title: "공통/PageIntro",
    component: PageIntro,
    decorators: [
        (Story) => (
            <div className="w-[min(720px,100%)] p-6">
                <Story/>
            </div>
        ),
    ],
    args: {
        eyebrow: "MY TICKETS",
        title: "내 티켓",
        description: "결제 완료 후 발급된 모바일 티켓을 확인합니다.",
    },
} satisfies Meta<typeof PageIntro>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const SectionHeading: Story = {
    args: {
        eyebrow: "— GAME CALENDAR",
        headingLevel: 2,
        title: "경기 일정",
        description: "날짜와 구단, 구장을 선택해 예매 상태를 확인하세요.",
    },
};
