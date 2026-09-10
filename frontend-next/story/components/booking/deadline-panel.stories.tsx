import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {DeadlinePanel} from "@/components/booking/deadline-panel";

const meta = {
    title: "예매/DeadlinePanel",
    component: DeadlinePanel,
    decorators: [
        (Story) => (
            <div className="w-[min(420px,100%)] p-6">
                <Story/>
            </div>
        ),
    ],
    args: {
        label: "결제 남은 시간",
        target: null,
        expired: false,
    },
} satisfies Meta<typeof DeadlinePanel>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Running: Story = {
    render: () => (
        <DeadlinePanel
            expired={false}
            label="결제 남은 시간"
            target={new Date(Date.now() + 5 * 60_000).toISOString()}
        />
    ),
};

export const Expired: Story = {
    args: {
        expired: true,
        expiredMessage: "결제시간이 만료되었습니다. 주문 상태를 확인해주세요.",
        target: "2000-01-01T00:00:00Z",
    },
};

export const UsedToken: Story = {
    args: {
        fallbackValue: "사용 완료",
        label: "입장 토큰",
    },
};
