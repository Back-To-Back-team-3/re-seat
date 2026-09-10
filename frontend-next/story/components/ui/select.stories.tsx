import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";

const meta = {
    title: "공통/Select",
    component: SelectTrigger,
    parameters: {
        layout: "centered",
    },
} satisfies Meta<typeof SelectTrigger>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
    render: () => (
        <Select defaultValue="ALL">
            <SelectTrigger className="w-40">
                <SelectValue>
                    {(value) => (value === "ALL" ? "전체 상태" : String(value))}
                </SelectValue>
            </SelectTrigger>
            <SelectContent>
                <SelectItem value="ALL">전체 상태</SelectItem>
                <SelectItem value="OPEN">예매중</SelectItem>
                <SelectItem value="CLOSED">예매 종료</SelectItem>
            </SelectContent>
        </Select>
    ),
};

export const Disabled: Story = {
    render: () => (
        <Select defaultValue="ALL" disabled>
            <SelectTrigger className="w-40">
                <SelectValue>{() => "전체 상태"}</SelectValue>
            </SelectTrigger>
        </Select>
    ),
};
