import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { Meta, StoryObj } from "@storybook/nextjs-vite";

import { userKeys } from "@/api/query-keys/users";
import { Header } from "@/components/layout/header";
import type { UserProfile } from "@/types/auth";

const profile: UserProfile = {
  id: 1,
  email: "fan@example.com",
  name: "테스트 사용자",
  nickname: "야구팬",
  phone: null,
  isVerified: true,
};

/**
 * 로그인 여부에 따라 상단 바가 달라지므로 스토리마다 인증 저장소와 프로필
 * 캐시를 함께 준비한다. Header는 useAuth로 두 값을 읽으므로 둘 중 하나만
 * 준비하면 실제 화면과 다른 중간 상태가 보인다.
 */
function withAuth(authed: boolean) {
  return function Decorator(Story: () => React.ReactElement) {
    if (authed) {
      localStorage.setItem("accessToken", "storybook-token");
    } else {
      localStorage.removeItem("accessToken");
    }

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    if (authed) {
      queryClient.setQueryData(userKeys.me(), profile);
    }

    return (
      <QueryClientProvider client={queryClient}>
        <Story />
      </QueryClientProvider>
    );
  };
}

const meta = {
  title: "레이아웃/Header",
  component: Header,
} satisfies Meta<typeof Header>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Guest: Story = {
  decorators: [withAuth(false)],
};

export const Authenticated: Story = {
  decorators: [withAuth(true)],
};

export const AuthenticatedDark: Story = {
  decorators: [withAuth(true)],
  globals: {
    theme: "dark",
  },
};

/** 900px 이하에서 메뉴가 숨고 상단 바 높이가 62px로 줄어드는지 확인한다. */
export const Mobile: Story = {
  decorators: [withAuth(true)],
  globals: {
    viewport: { value: "mobile1" },
  },
};
