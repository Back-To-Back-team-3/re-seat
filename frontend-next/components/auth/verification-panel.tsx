"use client";

import Script from "next/script";
import {useState} from "react";
import {LogOut, ShieldCheck} from "lucide-react";

import {
    VerificationAgencySelector,
    type VerificationAgency,
} from "@/components/auth/verification-agency-selector";
import {VerificationSteps} from "@/components/auth/verification-steps";
import {Button} from "@/components/ui/button";

const PORTONE_CODE =
    process.env.NEXT_PUBLIC_PORTONE_CODE ?? "imp31640540";
const PORTONE_PG =
    process.env.NEXT_PUBLIC_PORTONE_PG ?? "inicis_unified";

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
                        <VerificationSteps/>
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
                        <VerificationAgencySelector
                            onChange={setSelectedAgency}
                            value={selectedAgency}
                        />
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
                        <Button
                            className="w-full text-[13px]"
                            loading={busy}
                            onClick={startVerification}
                            type="button"
                        >
                            {!busy && <ShieldCheck aria-hidden="true"/>}
                            {busy ? "인증 요청 처리 중..." : "본인인증 시작하기"}
                        </Button>
                        <Button
                            className="mt-2 w-full text-[10px] text-muted-foreground"
                            onClick={onLogout}
                            size="sm"
                            type="button"
                            variant="ghost"
                        >
                            <LogOut aria-hidden="true"/>
                            로그아웃 후 다른 계정으로 로그인
                        </Button>
                    </div>
                </section>
            </main>
        </>
    );
}
