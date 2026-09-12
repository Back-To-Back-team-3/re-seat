import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {StadiumZoneMap} from "@/components/congestion/stadium-zone-map";
import {calculateStadiumZones} from "@/lib/stadium-zones";
import {storyCongestion} from "@/story/fixtures";

const spots = calculateStadiumZones(storyCongestion);

const meta = {
    title: "혼잡도/StadiumZoneMap",
    component: StadiumZoneMap,
    decorators: [
        (Story) => (
            <div className="min-h-115 w-full">
                <Story/>
            </div>
        ),
    ],
    args: {
        spots,
        selectedSpotId: null,
        onSelect: () => undefined,
    },
} satisfies Meta<typeof StadiumZoneMap>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Selected: Story = {
    args: {selectedSpotId: spots[0].id},
};

export const Empty: Story = {
    args: {spots: []},
};
