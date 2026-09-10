import type {Meta, StoryObj} from "@storybook/nextjs-vite";
import {ArrowRight, Plus} from "lucide-react";

import {Button} from "@/components/ui/button";

const meta = {
    title: "공통/Button",
    component: Button,
    parameters: {
        layout: "centered",
    },
    args: {
        children: "결제하기",
    },
} satisfies Meta<typeof Button>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Variants: Story = {
    render: () => (
        <div className="flex flex-wrap items-center gap-3">
            <Button>기본</Button>
            <Button variant="secondary">보조</Button>
            <Button variant="outline">외곽선</Button>
            <Button variant="ghost">고스트</Button>
            <Button variant="destructive">삭제</Button>
            <Button variant="link">자세히 보기</Button>
        </div>
    ),
};

export const Sizes: Story = {
    render: () => (
        <div className="flex items-center gap-3">
            <Button size="sm">작게</Button>
            <Button>기본</Button>
            <Button size="lg">크게</Button>
        </div>
    ),
};

export const WithIcon: Story = {
    render: () => (
        <div className="flex items-center gap-3">
            <Button>
                새 주문
                <Plus aria-hidden="true"/>
            </Button>
            <Button variant="outline">
                다음 단계
                <ArrowRight aria-hidden="true"/>
            </Button>
            <Button aria-label="새 주문" size="icon" variant="outline">
                <Plus aria-hidden="true"/>
            </Button>
        </div>
    ),
};

export const Loading: Story = {
    args: {
        children: "처리 중",
        loading: true,
    },
};

export const Disabled: Story = {
    args: {
        disabled: true,
    },
};
