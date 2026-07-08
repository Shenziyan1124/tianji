package com.tianji.learning.utils;

import com.tianji.common.utils.JsonUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


@Component
@Slf4j
@RequiredArgsConstructor
public class LearningRecordRedissonDelayTaskHandler {

    private final StringRedisTemplate redisTemplate;
    private final LearningRecordMapper recordMapper;
    private final ILearningLessonService learningLessonService;
    private final RedissonClient redissonClient;

    //private final DelayQueue<DelayTask<RecordTaskData>> queue = new DelayQueue<>();
    private final static String RECORD_KEY_TEMPLATE = "learning:record:{}";
    private static final String DELAY_QUEUE_NAME = "learning:delay:queue";
    private RBlockingDeque<RecordTaskData> blockingDeque;
    private RDelayedQueue<RecordTaskData> delayedQueue;


    private static volatile boolean begin = true;


    // 线程池
    private final Integer threadPoolSize = 2;
    private final ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);

    @PostConstruct
    public void init(){
        // 启动延迟任务处理线程 异步
        // CompletableFuture.runAsync(this::handleDelayTask);

        blockingDeque = redissonClient.getBlockingDeque(DELAY_QUEUE_NAME);
        delayedQueue = redissonClient.getDelayedQueue(blockingDeque);


        // 启用线程池
        for (int i = 0; i < threadPoolSize; i++){
            executor.submit(this::handleDelayTask);
        }
        log.info("延迟任务处理线程池已启动，线程数: {}", threadPoolSize);
    }

    @PreDestroy
    public void destroy() {
        begin = false;

        if (delayedQueue != null){
            delayedQueue.destroy();
        }
        executor.shutdown();
        // 等待线程池中的任务执行完成
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)){
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            log.error("延迟任务处理线程停止异常", e);
        }
        log.info("延迟任务处理线程已停止");
    }


    public void handleDelayTask() {
        while (begin) {
            try {
                // 1. 获取延迟任务
                RecordTaskData data = blockingDeque.take();
                //                DelayTask<RecordTaskData> take = queue.take();
//                RecordTaskData data = take.getData();
                // 2. 查询redis缓存
                LearningRecord learningRecord = readRecordCache(data.getLessonId(), data.getSectionId());
                //LearningRecord learningRecord = readRecordCache(data.getLessonId(), data.getSectionId());
                if (learningRecord == null) continue;

                // 3. 比较
                if (!Objects.equals(learningRecord.getMoment(), data.getMoment())) {
                    // 如果值不一致,代表用户还在学习,放弃旧的值
                    continue;
                }

                // 4. 如果一致,代表不学习, 更新值
                // 4.1 更新学习记录的moment
                learningRecord.setFinished(null);
                recordMapper.updateById(learningRecord);
                // 4.2 更新课表的学习状态
                LearningLesson learningLesson = new LearningLesson();
                learningLesson.setId(data.getLessonId());
                learningLesson.setLatestSectionId(data.getSectionId());
                learningLesson.setLatestLearnTime(LocalDateTime.now());
                learningLessonService.updateById(learningLesson);

            } catch (InterruptedException e) {
                // 线程被中断（如应用关闭时），恢复中断标志并退出循环
                log.info("延迟任务处理线程被中断，准备退出");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("处理延迟任务异常", e);
            }
        }
    }

    // 添加学习记录任务
    public void addLearningRecordTask(LearningRecord record) {
        // 1. 添加数据到缓存
        writeRecordCache(record);
        // 2. 提交延迟任务到延迟队列
        delayedQueue.offer(new RecordTaskData(record),20, TimeUnit.SECONDS);
//        queue.add(
//                new DelayTask<>(
//                        Duration.ofSeconds(20), new RecordTaskData(record)
//                )
//        );
    }

    public void writeRecordCache(LearningRecord record) {
        log.debug("更新学习记录的缓存数据");
        try {
            //1.数据转换
            String json = JsonUtils.toJsonStr(new RecordCacheData(record));
            //2.写入redis
            String key = StringUtils.format(RECORD_KEY_TEMPLATE, record.getLessonId().toString());
            redisTemplate.opsForHash().put(key, record.getSectionId().toString(), json);
            //3.设置过期时间
            redisTemplate.expire(key, Duration.ofMinutes(1));
        } catch (Exception e) {
            log.error("更新学习记录的缓存数据异常", e);
        }
    }


    public LearningRecord readRecordCache(Long lessonId, Long sectionId) {
        log.debug("读取学习记录的缓存数据");
        try {
            // 读取
            String key = StringUtils.format(RECORD_KEY_TEMPLATE, lessonId);
            Object o = redisTemplate.opsForHash().get(key, sectionId.toString());
            if (o == null) return null;
            // 转换数据
            return JsonUtils.toBean(o.toString(), LearningRecord.class);
        } catch (Exception e) {
            log.error("读取学习记录的缓存数据异常", e);
            return null;
        }
    }

    public void deleteRecordCache(Long lessonId, Long sectionId) {
        log.debug("删除学习记录的缓存数据");
        try {
            String key = StringUtils.format(RECORD_KEY_TEMPLATE, lessonId);
            redisTemplate.opsForHash().delete(key, sectionId.toString());
        } catch (Exception e) {
            log.error("删除学习记录的缓存数据异常", e);
        }
    }


    @Data
    @NoArgsConstructor
    private static class RecordCacheData {
        private Long id;
        private Integer moment;
        private Boolean finished;

        public RecordCacheData(LearningRecord record) {
            this.id = record.getId();
            this.moment = record.getMoment();
            this.finished = record.getFinished();
        }
    }


    @Data
    @NoArgsConstructor
    private static class RecordTaskData {
        private Long lessonId;
        private Long sectionId;
        private Integer moment;

        public RecordTaskData(LearningRecord record) {
            this.lessonId = record.getLessonId();
            this.sectionId = record.getSectionId();
            this.moment = record.getMoment();
        }
    }
}
