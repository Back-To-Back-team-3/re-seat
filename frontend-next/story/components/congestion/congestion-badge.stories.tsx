import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {CongestionBadge} from "@/components/congestion/congestion-badge";

const meta = {
    title: "혼잡도/CongestionBadge",
    component: CongestionBadge,
    decorators: [
        (Story) => (
            <div className="p-6">
                <Story/>
            </div>
        ),
    ],
    args: {level: "보통"},
} satisfies Meta<typeof CongestionBadge>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Relaxed: Story = {args: {level: "여유"}};
export const Normal: Story = {};
export const SlightlyCrowded: Story = {args: {level: "약간 붐빔"}};
export const Crowded: Story = {args: {level: "붐빔"}};
