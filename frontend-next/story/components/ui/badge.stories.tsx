import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {Badge} from "@/components/ui/badge";

const meta = {
    title: "공통/Badge",
    component: Badge,
    parameters: {
        layout: "centered",
    },
    args: {
        children: "예매중",
    },
} satisfies Meta<typeof Badge>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Statuses: Story = {
    render: () => (
        <div className="flex flex-wrap items-center gap-3">
            <Badge>예매중</Badge>
            <Badge variant="secondary">준비중</Badge>
            <Badge variant="success">완료</Badge>
            <Badge variant="warning">처리중</Badge>
            <Badge variant="destructive">실패</Badge>
            <Badge variant="outline">전체</Badge>
        </div>
    ),
};
