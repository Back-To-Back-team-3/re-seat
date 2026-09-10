import {QueryClientProvider} from "@tanstack/react-query";
import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {congestionKeys} from "@/api/query-keys/congestion";
import {StadiumCongestionMap} from "@/components/congestion/stadium-congestion-map";
import {storyCongestion} from "@/story/fixtures";
import {createStoryQueryClient} from "@/story/query-client";

const queryClient = createStoryQueryClient();
queryClient.setQueryData(congestionKeys.stadium(1), storyCongestion);

const meta = {
    title: "혼잡도/StadiumCongestionMap",
    component: StadiumCongestionMap,
    decorators: [
        (Story) => (
            <QueryClientProvider client={queryClient}>
                <div className="w-[min(680px,100%)] p-6">
                    <Story/>
                </div>
            </QueryClientProvider>
        ),
    ],
    args: {stadiumNum: 1},
} satisfies Meta<typeof StadiumCongestionMap>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};
