import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {VerificationSteps} from "@/components/auth/verification-steps";

const meta = {
    title: "인증/VerificationSteps",
    component: VerificationSteps,
    decorators: [
        (Story) => (
            <div className="max-w-sm bg-foreground p-8 text-surface">
                <Story/>
            </div>
        ),
    ],
} satisfies Meta<typeof VerificationSteps>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};
