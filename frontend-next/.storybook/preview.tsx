import type {Preview} from "@storybook/nextjs-vite";

import "../app/globals.css";

const preview: Preview = {
    globalTypes: {
        theme: {
            description: "컴포넌트에 적용할 Re:Seat 색상 테마",
            toolbar: {
                icon: "contrast",
                items: [
                    {title: "라이트", value: "light"},
                    {title: "다크", value: "dark"},
                ],
            },
        },
    },
    initialGlobals: {
        theme: "light",
    },
    decorators: [
        (Story, context) => {
            // 실제 앱과 동일한 data-theme 속성을 문서 루트에 적용한다.
            // Storybook toolbar에서 바꾸면 토큰 기반 색상이 즉시 함께 변경된다.
            document.documentElement.dataset.theme = context.globals.theme;

            return (
                <div className="min-h-screen bg-background text-foreground">
                    <Story/>
                </div>
            );
        },
    ],
    parameters: {
        controls: {
            matchers: {
                color: /(background|color)$/i,
                date: /Date$/i,
            },
        },
    },
};

export default preview;
