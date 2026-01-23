package com.leets.commitatobe.domain.commit.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.leets.commitatobe.global.exception.ApiException;
import com.leets.commitatobe.global.response.code.status.ErrorStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Getter
@Slf4j
public class GitHubService {
	private final String GITHUB_API_URL = "https://api.github.com";
	private final WebClient webClient = WebClient.builder()
		.baseUrl(GITHUB_API_URL)
		.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
		.defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
		.exchangeStrategies(ExchangeStrategies.builder()
			.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024)) // 1MB
			.build())
		.build();

	// GitHub repository 이름 저장
	public List<String> fetchRepos(String accessToken) {
		Set<String> repoFullNames = new HashSet<>();
		LocalDateTime twoMonthsAgo = LocalDate.now().minusMonths(2).withDayOfMonth(1).atStartOfDay();

		JsonArray repos = getConnection("/user/repos?type=all&sort=pushed&per_page=100", accessToken);
		if (repos == null) {
			return new ArrayList<>();
		}

		repos.forEach(repo -> {
			JsonObject repoObject = repo.getAsJsonObject();
			String fullName = repoObject.get("full_name").getAsString();

			// pushed_at 필드로 최근 활동 확인
			if (repoObject.has("pushed_at") && !repoObject.get("pushed_at").isJsonNull()) {
				String pushedAtStr = repoObject.get("pushed_at").getAsString();
				try {
					Instant pushedAt = Instant.parse(pushedAtStr);
					LocalDateTime pushedDate = LocalDateTime.ofInstant(pushedAt, ZoneId.of("Asia/Seoul"));

					// 최근 2개월 이내에 push가 있었던 레포지토리만 추가
					if (pushedDate.isAfter(twoMonthsAgo)) {
						repoFullNames.add(fullName);
					}
				} catch (Exception e) {
					// 날짜 파싱 실패 시 안전하게 포함
					repoFullNames.add(fullName);
				}
			} else {
				// pushed_at 정보가 없으면 안전하게 포함
				repoFullNames.add(fullName);
			}
		});

		return new ArrayList<>(repoFullNames);
	}

	// commit을 일별로 정리 (페이지네이션 병렬 처리)
	public void countCommits(String accessToken, String fullName, String gitHubUsername, LocalDateTime date,
		Map<LocalDateTime, Integer> commitsByDate) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

		// 첫 페이지 조회로 커밋 존재 여부 확인
		JsonArray firstPageCommits;
		try {
			firstPageCommits = fetchCommitPage(accessToken, fullName, gitHubUsername, date, 1);
		} catch (Exception e) {
			return;
		}

		if (firstPageCommits == null || firstPageCommits.isEmpty()) {
			return;
		}

		// 첫 페이지 데이터 처리
		processCommits(firstPageCommits, date, commitsByDate, formatter);

		// 첫 페이지가 100개 미만이면 더 이상 페이지가 없음
		if (firstPageCommits.size() < 100) {
			return;
		}

		// 페이지 2부터 배치 단위로 병렬 조회
		int currentBatch = 0;  // 0부터 시작으로 수정
		int batchSize = 5; // 5페이지씩 병렬 조회
		int totalPages = 1;

		while (true) {
			int batchStart = currentBatch * batchSize + 2;  // +2로 수정 (페이지 1은 이미 처리)
			int batchEnd = batchStart + batchSize - 1;

			// 배치 내의 모든 페이지를 병렬로 조회
			List<CompletableFuture<JsonArray>> futures = IntStream.rangeClosed(batchStart, batchEnd)
				.mapToObj(pageNum -> CompletableFuture.supplyAsync(() -> {
					try {
						return fetchCommitPage(accessToken, fullName, gitHubUsername, date, pageNum);
					} catch (Exception e) {
						return null;
					}
				}, pageExecutor))
				.toList();

			// 모든 페이지 조회 완료 대기
			List<JsonArray> results = futures.stream()
				.map(CompletableFuture::join)
				.toList();

			// 결과 처리 (순서대로 처리하며 조기 종료)
			boolean hasMorePages = false;
			for (JsonArray commits : results) {
				if (commits == null || commits.isEmpty()) {
					// 빈 페이지 발견 시 종료
					break;
				}

				processCommits(commits, date, commitsByDate, formatter);
				totalPages++;

				if (commits.size() == 100) {
					// 100개면 다음 페이지가 있을 가능성 있음
					hasMorePages = true;
				} else {
					// 100개 미만이면 마지막 페이지이므로 종료
					hasMorePages = false;
					break;
				}
			}

			// 더 이상 페이지가 없으면 종료
			if (!hasMorePages) {
				break;
			}

			currentBatch++;

			// 안전장치: 최대 100페이지 (10,000개 커밋)까지만 조회
			if (totalPages >= 100) {
				log.warn("레포지토리 {}의 커밋이 100페이지(10,000개)를 초과하여 조회를 중단합니다.", fullName);
				break;
			}
		}
	}

	// 페이지 병렬 조회용 ExecutorService (5개 스레드)
	private final ExecutorService pageExecutor = Executors.newFixedThreadPool(5);

	// 단일 페이지의 커밋 조회
	private JsonArray fetchCommitPage(String accessToken, String fullName, String gitHubUsername,
		LocalDateTime date, int page) {
		return getConnection(
			"/repos/" + fullName + "/commits?page=" + page + "&per_page=100&since=" + formatToISO8601(date)
				+ "&author=" + gitHubUsername, accessToken);
	}

	// 커밋 데이터 처리
	private void processCommits(JsonArray commits, LocalDateTime date, Map<LocalDateTime, Integer> commitsByDate,
		DateTimeFormatter formatter) {
		for (int i = 0; i < commits.size(); i++) {
			JsonObject commit = commits.get(i).getAsJsonObject();

			String commitDateTime = getCommitDateTime(commit);
			if (commitDateTime.length() < 10) {
				continue;
			}

			LocalDateTime commitDate = LocalDate.parse(commitDateTime.substring(0, 10), formatter).atStartOfDay();
			if (commitDate.isBefore(date)) {
				continue;
			}
			commitsByDate.merge(commitDate, 1, Integer::sum);
		}
	}

	// http 연결
	private JsonArray getConnection(String url, String accessToken) {
		return webClient.get()
			.uri(url)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
			.retrieve()
			.onStatus(s -> s.value() == 401,
				r -> Mono.error(new ApiException(ErrorStatus._UNAUTHORIZED)))
			.bodyToMono(String.class)
			.map(res -> JsonParser.parseString(res).getAsJsonArray())
			.block();
	}

	// commit 시간 추출
	private String getCommitDateTime(JsonObject commit) {
		String originCommitDateTime = commit.get("commit").getAsJsonObject() // UTC+0
			.get("author").getAsJsonObject()
			.get("date").getAsString();
		Instant instant = Instant.parse(originCommitDateTime);

		ZoneId kst = ZoneId.of("Asia/Seoul");
		return instant.atZone(kst).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	}

	// GitHub API에서 제공하는 시간 표현법으로 변환
	private String formatToISO8601(LocalDateTime dateTime) {
		ZoneId kst = ZoneId.of("Asia/Seoul");
		Instant instant = dateTime.atZone(kst).toInstant();

		return DateTimeFormatter.ISO_INSTANT.format(instant);
	}
}
