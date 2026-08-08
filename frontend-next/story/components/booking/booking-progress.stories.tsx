import type { Meta, StoryObj } from "@storybook/nextjs-vite";

import { BookingProgress } from "@/components/booking/booking-progress";

const meta = {
  title: "예매/BookingProgress",
  component: BookingProgress,
  decorators: [
    (Story) => (
      <div className="bg-surface p-6">
        <Story />
      </div>
    ),
  ],
  args: {
    activeStep: "queue",
  },
} satisfies Meta<typeof BookingProgress>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Queue: Story = {};

export const Seats: Story = {
  args: {
    activeStep: "seats",
  },
};

export const Checkout: Story = {
  args: {
    activeStep: "checkout",
  },
};

export const Payment: Story = {
  args: {
    activeStep: "payment",
  },
};
