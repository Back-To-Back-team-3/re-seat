import {QueryClient} from "@tanstack/react-query";

/** Storybook의 데이터 조회 컴포넌트가 외부 API 없이 렌더링되도록 QueryClient를 만든다. */
export function createStoryQueryClient() {
    return new QueryClient({
        defaultOptions: {
            queries: {
                retry: false,
                staleTime: Number.POSITIVE_INFINITY,
            },
        },
    });
}
