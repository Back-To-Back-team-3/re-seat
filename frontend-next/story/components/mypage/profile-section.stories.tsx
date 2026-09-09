import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {ProfileSection} from "@/components/mypage/profile-section";
import {storyProfile} from "@/story/fixtures";

const meta = {
    title: "마이페이지/ProfileSection",
    component: ProfileSection,
    parameters: {layout: "fullscreen"},
    decorators: [
        (Story) => (
            <div className="p-6">
                <Story/>
            </div>
        ),
    ],
    args: {
        profile: storyProfile,
        role: "USER",
        isVerified: true,
        onLogout: () => undefined,
        onWithdraw: () => undefined,
        isWithdrawing: false,
    },
} satisfies Meta<typeof ProfileSection>;

export default meta;
type Story = StoryObj<typeof meta>;

export const User: Story = {};

export const Admin: Story = {
    args: {role: "ADMIN"},
};

export const Unverified: Story = {
    args: {isVerified: false},
};

export const Withdrawing: Story = {
    args: {isWithdrawing: true},
};
