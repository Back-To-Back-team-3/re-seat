import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {AccountManagement} from "@/components/mypage/account-management";
import {storyProfile} from "@/story/fixtures";

const meta = {
    title: "마이페이지/AccountManagement",
    component: AccountManagement,
    parameters: {layout: "fullscreen"},
    decorators: [
        (Story) => (
            <div className="mx-auto max-w-4xl bg-surface">
                <Story/>
            </div>
        ),
    ],
    args: {
        profile: storyProfile,
        onUpdate: () => undefined,
        onWithdraw: () => undefined,
        isUpdating: false,
        isWithdrawing: false,
    },
} satisfies Meta<typeof AccountManagement>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Updating: Story = {
    args: {isUpdating: true},
};

export const Withdrawing: Story = {
    args: {isWithdrawing: true},
};
