import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {Footer} from "@/components/layout/footer";

const meta = {
    title: "레이아웃/Footer",
    component: Footer,
} satisfies Meta<typeof Footer>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

/** 640px 이하에서 브랜드와 저작권 영역이 한 열로 배치되는지 확인한다. */
export const Mobile: Story = {
    globals: {
        viewport: {value: "mobile1"},
    },
};
