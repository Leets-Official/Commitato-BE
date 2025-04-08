package com.leets.commitatobe.domain.commit.controller;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.leets.commitatobe.domain.commit.dto.response.ExpAndTierResponse;
import com.leets.commitatobe.domain.commit.service.ExpService;
import com.leets.commitatobe.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Exp, Tier 업데이트 컨트롤러", description = "입력한 Exp에 따른 사용자의 Exp와 Tier 데이터 가공을 처리합니다.")
@RestController
@RequiredArgsConstructor
@RequestMapping
public class ExpAndTierTestController {
	private final ExpService expService;

	@Operation(
		summary = "경험치 및 티어 업데이트",
		description = "테스트를 위해, 사용자가 경험치 입력 시 경험치와 티어를 해당 경험치에 맞게 업데이트합니다.")
	@PatchMapping("update/expAndTier")
	public ApiResponse<ExpAndTierResponse> updateExpAndTier(@RequestParam("exp") int exp) {
		return ApiResponse.onSuccess(expService.updateExpAndTier(exp));
	}
}
