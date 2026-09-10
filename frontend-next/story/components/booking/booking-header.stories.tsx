import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {BookingHeader} from "@/components/booking/booking-header";

const meta = {
    title: "예매/BookingHeader",
    component: BookingHeader,
    parameters: {layout: "fullscreen"},
} satisfies Meta<typeof BookingHeader>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Queue: Story = {
    parameters: {nextjs: {navigation: {pathname: "/games/111/queue"}}},
};
export const Seats: Story = {
    parameters: {nextjs: {navigation: {pathname: "/games/111/seats"}}},
};
export const Checkout: Story = {
    parameters: {nextjs: {navigation: {pathname: "/checkout"}}},
};
export const Payment: Story = {
    parameters: {nextjs: {navigation: {pathname: "/payments/401"}}},
};
