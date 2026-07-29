package com.backtoback.reseat.global.common;

//동시성 테스트
 //멀티스레드 요청 동시성 실행(ExecutorService, CountDownLatch 등) 헬퍼 기능 제공


import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public abstract class BaseConcurrencyTest extends BaseIntegrationTest {

    //지정한 횟수만큼 동시 요청 실행하는 헬퍼메서드
    //@Param ThreadCount 동시 실행 스레드 수
    //@Param task 각 스레드에서 수행할 작업
    //@return 성공한 작업 횟수
    protected int executeConcurrentTasks(int threadCount, Consumer<Integer> task) throws InterruptedException {

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executorService.submit(() -> {
                try {
                    task.accept(threadIndex);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 예외 발생 시 로그 출력 후 실패 처리
                    System.err.println("동시성 테스트 스레드 예외 발생: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 모든 스레드가 끝날 때까지 대기
        executorService.shutdown();

        return successCount.get();
    }
}
