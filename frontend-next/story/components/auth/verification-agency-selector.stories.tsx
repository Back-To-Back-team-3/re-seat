import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {VerificationAgencySelector} from "@/components/auth/verification-agency-selector";

const meta = {
    title: "인증/VerificationAgencySelector",
    component: VerificationAgencySelector,
    args: {
        value: "PASS",
        onChange: () => undefined,
    },
} satisfies Meta<typeof VerificationAgencySelector>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Pass: Story = {};
export const Toss: Story = {args: {value: "TOSS"}};
export const FinancialCertificate: Story = {args: {value: "KFTC"}};
