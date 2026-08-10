import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {Alert} from "@/components/common/alert";

const meta = {
    title: "공통/Alert",
    component: Alert,
    parameters: {
        layout: "fullscreen",
    },
    args: {
        message: "좌석 선점이 완료되었습니다.",
        variant: "success",
        onClose: () => undefined,
    },
} satisfies Meta<typeof Alert>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Success: Story = {};

export const Error: Story = {
    args: {
        message: "좌석 선택 가능 시간이 만료되었습니다.",
        variant: "error",
    },
};

export const WithoutCloseButton: Story = {
    args: {
        onClose: undefined,
    },
};
