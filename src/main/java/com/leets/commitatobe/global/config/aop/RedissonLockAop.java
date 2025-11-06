package com.leets.commitatobe.global.config.aop;

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import com.leets.commitatobe.global.config.parser.CustomSpringELParser;
import com.leets.commitatobe.global.config.redis.annotation.RedissonLock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class RedissonLockAop {

	private final RedissonClient redissonClient;
	private final AopForTransaction aopForTransaction;

	@Around("@annotation(com.leets.commitatobe.global.config.redis.annotation.RedissonLock)")
	public Object lock(final ProceedingJoinPoint joinPoint) throws Throwable {
		MethodSignature signature = (MethodSignature)joinPoint.getSignature();
		Method method = signature.getMethod();
		RedissonLock redissonLock = method.getAnnotation(RedissonLock.class);

		String key = (String)CustomSpringELParser
			.getDynamicValue(signature.getParameterNames(), joinPoint.getArgs(), redissonLock.key());
		RLock rLock = redissonClient.getLock(key);

		try {
			boolean available = rLock.tryLock(redissonLock.waitTime(), redissonLock.leaseTime(),
				redissonLock.timeUnit());
			if (!available) {
				log.warn("Lock 획득 실패 : {}", key);
				return false;
			}

			return aopForTransaction.proceed(joinPoint, key);
		} catch (InterruptedException e) {
			throw new InterruptedException();
		} finally {
			try {
				log.info("Lock 해제 : {}", key);
				rLock.unlock();
			} catch (IllegalMonitorStateException e) {
				log.warn("이미 해제된 Lock : {} {}", method.getName(), key);
			}
		}
	}
}
