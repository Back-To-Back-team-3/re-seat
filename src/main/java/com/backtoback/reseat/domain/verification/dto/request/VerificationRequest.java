package com.backtoback.reseat.domain.verification.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class VerificationRequest {

     private String impUid;

     public VerificationRequest(String impUid){
         this.impUid = impUid;
     }
}
