import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {MyPageNavigation} from "@/components/mypage/mypage-navigation";

const meta = {
    title: "마이페이지/MyPageNavigation",
    component: MyPageNavigation,
    args: {
        activeTab: "tickets",
        onSelect: () => undefined,
    },
} satisfies Meta<typeof MyPageNavigation>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Tickets: Story = {};

export const Account: Story = {
    args: {activeTab: "account"},
};
