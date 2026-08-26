import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {Countdown} from "@/components/common/countdown";

const meta = {
    title: "공통/Countdown",
    component: Countdown,
    decorators: [
        (Story) => (
            <div className="flex min-h-40 items-center justify-center">
                <Story/>
            </div>
        ),
    ],
    args: {
        target: null,
    },
} satisfies Meta<typeof Countdown>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Running: Story = {
    render: () => (
        <Countdown target={new Date(Date.now() + 65_000).toISOString()}/>
    ),
};

export const Expired: Story = {
    args: {
        target: "2000-01-01T00:00:00Z",
    },
};

export const InvalidTarget: Story = {
    args: {
        target: "만료 시각 확인 불가",
    },
};
