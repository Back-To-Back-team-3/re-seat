import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {LoginPanel} from "@/components/auth/login-panel";
import {storyProfile} from "@/story/fixtures";

const meta = {
    title: "인증/LoginPanel",
    component: LoginPanel,
    args: {
        isAuthed: false,
        profile: null,
        role: null,
        onLogin: () => undefined,
        onLogout: () => undefined,
    },
} satisfies Meta<typeof LoginPanel>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Guest: Story = {};

export const User: Story = {
    args: {isAuthed: true, profile: storyProfile, role: "USER"},
};

export const Admin: Story = {
    args: {isAuthed: true, profile: storyProfile, role: "ADMIN"},
};
