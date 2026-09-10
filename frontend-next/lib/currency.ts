/** 가격 필드를 가진 항목들의 총액을 계산한다. */
export function calculateTotalPrice(items: ReadonlyArray<{price: number}>) {
    return items.reduce((sum, item) => sum + item.price, 0);
}

export function formatPrice(value: number) {
    return `${new Intl.NumberFormat("ko-KR").format(value)}원`;
}
