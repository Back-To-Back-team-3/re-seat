import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {VerificationPanel} from "@/components/auth/verification-panel";

const meta = {
    title: "인증/VerificationPanel",
    component: VerificationPanel,
    args: {
        busy: false,
        onError: () => undefined,
        onLogout: () => undefined,
        onVerify: () => undefined,
    },
    parameters: {layout: "fullscreen"},
} satisfies Meta<typeof VerificationPanel>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};
export const Verifying: Story = {args: {busy: true}};
