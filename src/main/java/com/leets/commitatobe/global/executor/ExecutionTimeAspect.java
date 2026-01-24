package com.leets.commitatobe.global.executor;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import lombok.extern.slf4j.Slf4j;

@Aspect
@Component
@Profile({"local"})
@Slf4j
public class ExecutionTimeAspect {

	@Around("@annotation(LogExecutionTime)")
	public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		Object proceed = joinPoint.proceed(); // 실제 메서드 실행

		stopWatch.stop();
		log.info("⏱️ [Performance] Method: {} | Execution Time: {} ms",
			joinPoint.getSignature().toShortString(),
			stopWatch.getTotalTimeMillis());

		return proceed;
	}
}
