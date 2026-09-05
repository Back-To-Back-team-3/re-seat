"use client";

import Script from "next/script";
import {useState} from "react";

const PORTONE_CODE =
    process.env.NEXT_PUBLIC_PORTONE_CODE ?? "imp31640540";
const PORTONE_PG =
    process.env.NEXT_PUBLIC_PORTONE_PG ?? "inicis_unified";

export type VerificationAgency = "PASS" | "TOSS" | "KFTC";

export const VERIFICATION_AGENCIES = [
    {
        id: "PASS" as const,
        name: "PASS",
        badge: "통신사",
        description: "SKT · KT · LGU+ 패스 앱",
    },
    {
        id: "TOSS" as const,
        name: "토스",
        badge: "간편인증",
        description: "토스 앱 간편 본인확인",
    },
    {
        id: "KFTC" as const,
        name: "금융인증서",
        badge: "은행",
        description: "은행 공동 금융결제원 인증",
    },
] as const;

type CertificationResponse = {
    success: boolean;
    imp_uid: string;
    error_msg?: string;
};

type InicisUnifiedBypass = {
    flgFixedUser: "Y" | "N";
    directAgency?: VerificationAgency;
    logoUrl?: string;
};

type CertificationOptions = {
    pg: string;
    merchant_uid: string;
    popup: true;
    bypass?: {
        inicisUnified: InicisUnifiedBypass;
    };
};

type PortOne = {
    init(code: string): void;
    certification(
        options: CertificationOptions,
        callback: (response: CertificationResponse) => void,
    ): void;
};

declare global {
    interface Window {
        IMP?: PortOne;
    }
}

type VerificationPanelProps = {
    busy: boolean;
    onError: (message: string) => void;
    onLogout: () => void;
    onVerify: (impUid: string) => void;
};

const verificationSteps = [
    {
        number: "1",
        title: "카카오 로그인",
        description: "계정 확인 완료",
        active: true,
    },
    {
        number: "2",
        title: "휴대폰 본인인증",
        description: "현재 단계",
        active: true,
    },
    {
        number: "3",
        title: "경기 예매 시작",
        description: "인증 후 바로 이용",
        active: false,
    },
] as const;

/**
 * PortOne 브라우저 SDK를 사용해 휴대폰 본인인증을 시작한다.
 *
 * SDK 호출과 외부 callback 해석만 담당하고, 백엔드 검증 요청과 프로필 갱신은
 * useAuth가 소유한다. 따라서 이후 SDK가 바뀌어도 인증 서버 상태 흐름은 유지된다.
 */
export function VerificationPanel({
                                      busy,
                                      onError,
                                      onLogout,
                                      onVerify,
                                  }: VerificationPanelProps) {
    const [selectedAgency, setSelectedAgency] =
        useState<VerificationAgency>("PASS");

    function startVerification() {
        const portOne = window.IMP;
        if (!portOne) {
            onError("포트원 SDK를 불러오지 못했습니다.");
            return;
        }

        portOne.init(PORTONE_CODE);
        portOne.certification(
            {
                pg: PORTONE_PG,
                merchant_uid: `verification_${Date.now()}`,
                popup: true,
                bypass: {
                    inicisUnified: {
                        flgFixedUser: "N",
                        directAgency: selectedAgency,
                    },
                },
            },
            (response) => {
                if (!response.success) {
                    onError(
                        `본인인증 실패: ${
                            response.error_msg ?? "인증을 완료하지 못했습니다."
                        }`,
                    );
                    return;
                }

                onVerify(response.imp_uid);
            },
        );
    }

    return (
        <>
            <Script
                src="https://cdn.iamport.kr/v1/iamport.js"
                strategy="afterInteractive"
            />
            <main className="grid min-h-[calc(100vh-70px)] place-items-center px-[5vw] py-[60px]">
                <section
                    className="grid w-full max-w-[920px] overflow-hidden rounded-[18px] border border-border bg-surface shadow-card md:grid-cols-[1.05fr_0.95fr]">
                    <div
                        className="relative overflow-hidden bg-foreground p-8 text-surface before:absolute before:right-[-80px] before:bottom-[-110px] before:size-[300px] before:rounded-full before:border before:border-brand/50 before:content-[''] sm:p-[50px]">
            <span className="font-mono text-[11px] font-extrabold tracking-[2px] text-brand">
              IDENTITY CHECK
            </span>
                        <div
                            aria-hidden="true"
                            className="my-6 grid size-[60px] place-items-center rounded-full bg-brand text-2xl font-black text-white"
                        >
                            ✓
                        </div>
                        <h1 className="mb-[17px] text-[42px] leading-[1.05] font-black tracking-tight">
                            안전한 예매를 위한
                            <br/>
                            마지막 한 단계
                        </h1>
                        <p className="max-w-[380px] text-[11px] leading-[1.75] text-surface/65">
                            부정 예매를 막고 공정한 예매 기회를 제공하기 위해 최초 한
                            번만 본인 확인을 진행합니다.
                        </p>
                        <ol className="mt-8 grid list-none gap-4 p-0">
                            {verificationSteps.map((step, index) => (
                                <li
                                    className={[
                                        "flex items-center gap-3",
                                        step.active ? "opacity-100" : "opacity-45",
                                    ].join(" ")}
                                    key={step.number}
                                >
                  <span
                      className={[
                          "grid size-[29px] shrink-0 place-items-center rounded-full border font-mono text-[9px]",
                          index === 1
                              ? "border-brand bg-brand"
                              : "border-white/35",
                      ].join(" ")}
                  >
                    {step.number}
                  </span>
                                    <div className="grid gap-0.5">
                                        <strong className="text-[11px]">{step.title}</strong>
                                        <small className="text-[8px] opacity-70">
                                            {step.description}
                                        </small>
                                    </div>
                                </li>
                            ))}
                        </ol>
                    </div>

                    <div className="grid content-center p-8 sm:p-[50px]">
            <span className="justify-self-start rounded-[20px] bg-brand/8 px-2 py-1 text-[9px] font-black text-brand">
              최초 1회
            </span>
                        <h2 className="mt-3.5 mb-2 text-[32px] font-black tracking-tight">
                            휴대폰 본인인증
                        </h2>
                        <p className="text-[11px] leading-[1.7] text-muted-foreground">
                            본인 명의의 휴대폰으로 인증하면 Re:Seat의 모든 예매 기능을
                            이용할 수 있습니다.
                        </p>
                        <div className="mt-4 mb-1">
                            <span className="mb-2 block text-[11px] font-bold text-foreground">
                                인증 수단 선택
                            </span>
                            <div className="grid grid-cols-3 gap-2">
                                {VERIFICATION_AGENCIES.map((agency) => {
                                    const isSelected = selectedAgency === agency.id;
                                    return (
                                        <button
                                            key={agency.id}
                                            type="button"
                                            onClick={() => setSelectedAgency(agency.id)}
                                            className={[
                                                "flex flex-col items-center justify-center rounded-control border p-2.5 text-center transition duration-fast cursor-pointer",
                                                isSelected
                                                    ? "border-brand bg-brand/8 text-brand font-bold shadow-xs ring-1 ring-brand"
                                                    : "border-border bg-surface text-muted-foreground hover:border-foreground/30 hover:bg-surface-soft",
                                            ].join(" ")}
                                        >
                                            <span className="text-[12px] font-extrabold">{agency.name}</span>
                                            <span className="mt-0.5 text-[9px] opacity-75">{agency.badge}</span>
                                        </button>
                                    );
                                })}
                            </div>
                        </div>
                        <div className="mt-[14px] mb-[22px] flex gap-3 rounded-control bg-surface-soft p-[13px]">
                            <span className="text-brand">⌕</span>
                            <div className="grid gap-0.5">
                                <strong className="text-[10px]">
                                    인증 정보는 안전하게 처리됩니다.
                                </strong>
                                <small className="text-[8px] text-muted-foreground">
                                    인증 결과는 중복 예매 방지와 회원 식별에만 사용됩니다.
                                </small>
                            </div>
                        </div>
                        <button
                            className="min-h-11 w-full cursor-pointer rounded-control border border-brand bg-brand px-[22px] text-[13px] font-extrabold text-white shadow-[0_8px_20px_rgb(224_53_53/20%)] transition duration-fast hover:bg-brand-dark disabled:cursor-not-allowed disabled:opacity-50"
                            disabled={busy}
                            onClick={startVerification}
                            type="button"
                        >
                            {busy ? "인증 요청 처리 중..." : "본인인증 시작하기"}
                        </button>
                        <button
                            className="mt-2 min-h-9 cursor-pointer border-0 bg-transparent px-2 text-[10px] text-muted-foreground"
                            onClick={onLogout}
                            type="button"
                        >
                            로그아웃 후 다른 계정으로 로그인
                        </button>
                    </div>
                </section>
            </main>
        </>
    );
}
