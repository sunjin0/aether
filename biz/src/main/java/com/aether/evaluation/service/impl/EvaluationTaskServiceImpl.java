package com.aether.evaluation.service.impl;
import com.aether.evaluation.entity.EvaluationTask; import com.aether.evaluation.mapper.EvaluationTaskMapper; import com.aether.evaluation.service.EvaluationTaskService; import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl; import com.baomidou.mybatisplus.core.toolkit.Wrappers; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.util.UUID;
@Service public class EvaluationTaskServiceImpl extends ServiceImpl<EvaluationTaskMapper, EvaluationTask> implements EvaluationTaskService {
 @Override @Transactional(rollbackFor = Exception.class)
 public EvaluationTask claim(String workerId, long leaseMillis) {
  long now=System.currentTimeMillis(); EvaluationTask task=getOne(Wrappers.lambdaQuery(EvaluationTask.class).eq(EvaluationTask::getStatus,"READY").and(q->q.isNull(EvaluationTask::getNextRunAt).or().le(EvaluationTask::getNextRunAt,now)).and(q->q.isNull(EvaluationTask::getLeaseUntil).or().lt(EvaluationTask::getLeaseUntil,now)).orderByAsc(EvaluationTask::getNextRunAt).last("FOR UPDATE SKIP LOCKED"),false);
  if(task==null)return null; task.setStatus("CLAIMED");task.setLeaseOwner(workerId);task.setLeaseToken(UUID.randomUUID().toString());task.setLeaseUntil(now+leaseMillis);task.setAttemptCount((task.getAttemptCount()==null?0:task.getAttemptCount())+1);updateById(task);return task;
 }
}
