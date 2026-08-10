export const ticketKeys = {
    all: ["tickets"] as const,
    list: () => [...ticketKeys.all, "list"] as const,
};
