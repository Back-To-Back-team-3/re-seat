import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {Button} from "@/components/ui/button";
import {
    Dialog,
    DialogClose,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
    DialogTrigger,
} from "@/components/ui/dialog";

const meta = {
    title: "공통/Dialog",
    component: Dialog,
    parameters: {
        layout: "centered",
    },
} satisfies Meta<typeof Dialog>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
    render: () => (
        <Dialog>
            <DialogTrigger render={<Button variant="outline"/>}>
                취소 요청
            </DialogTrigger>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>예매를 취소하시겠습니까?</DialogTitle>
                    <DialogDescription>
                        요청이 접수되면 결제 취소 상태를 확인할 수 있습니다.
                    </DialogDescription>
                </DialogHeader>
                <DialogFooter>
                    <DialogClose render={<Button variant="outline"/>}>
                        돌아가기
                    </DialogClose>
                    <Button>취소 요청</Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    ),
};
