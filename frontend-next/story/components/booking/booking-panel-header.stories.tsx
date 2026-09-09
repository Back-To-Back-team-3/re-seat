import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {BookingPanelHeader} from "@/components/booking/booking-panel-header";

const meta = {
    title: "예매/BookingPanelHeader",
    component: BookingPanelHeader,
    decorators: [
        (Story) => (
            <div className="w-[min(620px,100%)] border border-border bg-surface">
                <Story/>
            </div>
        ),
    ],
    args: {
        step: "02",
        title: "좌석 선택",
        description: "선택한 구역의 실제 좌석을 선택하세요.",
    },
} satisfies Meta<typeof BookingPanelHeader>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Confirmation: Story = {
    args: {
        step: "03",
        title: "선택 확인",
        description: "최대 2석까지 선택할 수 있습니다.",
    },
};
