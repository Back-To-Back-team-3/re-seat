package com.backtoback.reseat.domain.verification.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class VerificationResponse {
	private int code;
	private String message;
	private ResponseData response;

	@Getter
	@NoArgsConstructor
	public static class ResponseData {
		@JsonProperty("unique_key")
		private String unique_key;
		private String name;
	}
}
