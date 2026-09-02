/* eslint-disable @typescript-eslint/no-explicit-any */
declare global {
    interface Window {
        kakao?: {
            maps: {
                load: (callback: () => void) => void;
                LatLng: new (latitude: number, longitude: number) => any;
                Map: new (
                    container: HTMLElement,
                    options: {
                        center: any;
                        level: number;
                    }
                ) => any;
                Marker: new (options: {
                    position: any;
                    map?: any;
                }) => any;
                CustomOverlay: new (options: {
                    position: any;
                    content: HTMLElement | string;
                    map?: any;
                    yAnchor?: number;
                    xAnchor?: number;
                }) => any;
            };
        };
    }
}

export {};
