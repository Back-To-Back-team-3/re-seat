export type ApiResponse<T> = {
  success: boolean;
  errorCode: string | null;
  message: string;
  data: T | null;
};

export type PageResponse<T> = {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  isFirst: boolean;
  isLast: boolean;
};

export type ApiResult<T> = {
  data: T;
  source: "api" | "mock";
  message?: string;
};
