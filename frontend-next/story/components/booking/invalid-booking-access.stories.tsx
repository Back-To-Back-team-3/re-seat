import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {InvalidBookingAccess} from "@/components/booking/invalid-booking-access";

const meta = {
    title: "예매/InvalidBookingAccess",
    component: InvalidBookingAccess,
    parameters: {
        layout: "fullscreen",
    },
} satisfies Meta<typeof InvalidBookingAccess>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};
