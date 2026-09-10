import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {TicketList} from "@/components/tickets/ticket-list";
import {storyGames, storyTicket} from "@/story/fixtures";

const meta = {
    title: "티켓/TicketList",
    component: TicketList,
    parameters: {layout: "fullscreen"},
    decorators: [
        (Story) => (
            <div className="p-6">
                <Story/>
            </div>
        ),
    ],
    args: {
        tickets: [
            storyTicket,
            {
                ...storyTicket,
                ticketId: 502,
                ticketNo: "TICKET-20990912-002",
                seat: "1루 A A열 2번",
                status: "REFUND_PENDING",
            },
        ],
        games: storyGames,
        reloading: false,
        onReload: () => undefined,
        onCancelTicket: () => undefined,
        onRetryCancelTicket: () => undefined,
    },
} satisfies Meta<typeof TicketList>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Empty: Story = {
    args: {tickets: []},
};

export const Reloading: Story = {
    args: {reloading: true},
};
