package com.tianji.exam.Repository;

import com.tianji.exam.domain.po.ExamRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * <p>
 * 考试记录 MongoDB 数据访问层
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-02
 */
public interface ExamRecordRepository extends MongoRepository<ExamRecord, String> {

    /**
     * 查询某用户在某小节的考试记录
     */
    List<ExamRecord> findByUserIdAndSectionId(Long userId, Long sectionId);

    /**
     * 统计某用户在某小节指定类型(练习/考试)的考试次数
     */
    long countByUserIdAndCourseIdAndSectionIdAndType(Long userId, Long courseId, Long sectionId, Integer type);
}
