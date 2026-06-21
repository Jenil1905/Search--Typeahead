package com.example.typeahead.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * <h1>SchedulerConfig</h1>
 * 
 * <h3>Why this class exists:</h3>
 * This class configures the thread pool size for Spring's Task Scheduler.
 * 
 * <h3>What problem it solves:</h3>
 * By default, Spring's task scheduling framework uses a single-threaded executor. If you have multiple tasks
 * annotated with <code>@Scheduled</code> (e.g., database cleanup, log rotation, cache warmups, and batch writers),
 * they will execute sequentially. If one task hangs or runs long, it blocks all other scheduled tasks from running.
 * This class sets up a multi-threaded pool to prevent starvation.
 * 
 * <h3>How it works internally:</h3>
 * It instantiates a {@link ThreadPoolTaskScheduler} and sets its pool size. Spring will automatically use
 * this bean to coordinate all scheduled executions.
 * 
 * <h3>Common Viva Questions:</h3>
 * <ol>
 *   <li><b>Q: What is the default thread pool size for Spring Scheduled tasks?</b><br>
 *       A: The default pool size is 1. All tasks run on a single thread named <code>task-1</code>.</li>
 *   <li><b>Q: What happens if a task takes longer than its scheduling interval in a single-threaded pool?</b><br>
 *       A: The next execution will be delayed. It will wait until the current execution finishes, which can skew
 *       periodic metrics and data flushes (like our 30s batch writes).</li>
 * </ol>
 */
@Configuration
public class SchedulerConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        // Configure 2 threads to handle background tasks concurrently
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("typeahead-sched-");
        scheduler.initialize();
        return scheduler;
    }
}

/*
 ==================================================================================
 VIVA NOTES: CONCURRENCY IN SCHEDULING
 ==================================================================================
 1. ThreadPoolTaskScheduler wraps Java's ScheduledThreadPoolExecutor.
 2. A pool size of 2 is perfect for development. It ensures our 30-second batch writer has a dedicated
    thread and won't be blocked by other background processes.
 ==================================================================================
*/
