import type { Meta, StoryObj } from "@storybook/nextjs-vite";

import { EmptyState } from "@/components/common/empty-state";

const meta = {
  title: "공통/EmptyState",
  component: EmptyState,
  decorators: [
    (Story) => (
      <div className="mx-auto max-w-3xl p-6">
        <Story />
      </div>
    ),
  ],
  args: {
    title: "보유한 티켓이 없습니다.",
    description:
      "경기 예매와 결제를 완료하면 이곳에 티켓이 표시됩니다.",
  },
} satisfies Meta<typeof EmptyState>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};
