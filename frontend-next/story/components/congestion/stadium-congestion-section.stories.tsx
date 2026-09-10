import {QueryClientProvider} from "@tanstack/react-query";
import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {congestionKeys} from "@/api/query-keys/congestion";
import {StadiumCongestionSection} from "@/components/congestion/stadium-congestion-section";
import {storyCongestion} from "@/story/fixtures";
import {createStoryQueryClient} from "@/story/query-client";

const queryClient = createStoryQueryClient();
queryClient.setQueryData(congestionKeys.stadium(1), storyCongestion);

const meta = {
    title: "혼잡도/StadiumCongestionSection",
    component: StadiumCongestionSection,
    parameters: {layout: "fullscreen"},
    decorators: [
        (Story) => (
            <QueryClientProvider client={queryClient}>
                <div className="mx-auto max-w-[1120px] p-6">
                    <Story/>
                </div>
            </QueryClientProvider>
        ),
    ],
    args: {stadiumNum: 1},
} satisfies Meta<typeof StadiumCongestionSection>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};
