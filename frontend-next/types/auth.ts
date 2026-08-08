export type TokenResponse = {
  grantType: string;
  accessToken: string;
  refreshToken: string;
};

export type UserRole = "USER" | "ADMIN";

export type UserProfile = {
  id: number;
  email: string;
  name: string | null;
  nickname: string | null;
  phone: string | null;
  isVerified: boolean;
};
