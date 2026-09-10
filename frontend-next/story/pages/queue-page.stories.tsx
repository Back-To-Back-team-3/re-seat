import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {QueueScreen} from "@/components/queue/queue-screen";
import {storyGames, storyQueue} from "@/story/fixtures";

const meta = {
    title: "페이지/대기열",
    component: QueueScreen,
    parameters: {layout: "fullscreen"},
    decorators: [
        (Story) => (
            <div className="p-6">
                <Story/>
            </div>
        ),
    ],
    args: {
        game: storyGames[0],
        queue: storyQueue,
        initialRank: 200,
        error: null,
        busy: false,
        onRefresh: () => undefined,
        onCancel: () => undefined,
        onContinue: () => undefined,
    },
} satisfies Meta<typeof QueueScreen>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Waiting: Story = {};

export const RegistrationPending: Story = {
    args: {
        queue: {...storyQueue, registrationPending: true},
        initialRank: null,
    },
};

export const Admitted: Story = {
    args: {
        queue: {
            ...storyQueue,
            rank: 0,
            estimatedWaitSeconds: 0,
            queueStatus: "ADMITTED",
            admitted: true,
            queueToken: "QUEUE-TOKEN-2099",
            tokenExpiresAt: "2099-09-12 18:05:00",
        },
    },
};

export const Error: Story = {
    args: {error: "대기열 상태를 불러오지 못했습니다."},
};
