package com.aether.evaluation.service.impl;

import com.aether.evaluation.entity.EvaluationWorkerSlot;
import com.aether.evaluation.mapper.EvaluationWorkerSlotMapper;
import com.aether.evaluation.service.EvaluationWorkerSlotService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class EvaluationWorkerSlotServiceImpl extends ServiceImpl<EvaluationWorkerSlotMapper, EvaluationWorkerSlot> implements EvaluationWorkerSlotService {
    @Override @Transactional(rollbackFor = Exception.class)
    public EvaluationWorkerSlot claim(String phase,String taskId,long leaseMillis){long now=System.currentTimeMillis();EvaluationWorkerSlot slot=getOne(Wrappers.lambdaQuery(EvaluationWorkerSlot.class).eq(EvaluationWorkerSlot::getPhase,phase).eq(EvaluationWorkerSlot::getDeleted,false).and(q->q.isNull(EvaluationWorkerSlot::getTaskId).or().lt(EvaluationWorkerSlot::getLeaseUntil,now)).orderByAsc(EvaluationWorkerSlot::getSlotNo).last("LIMIT 1 FOR UPDATE SKIP LOCKED"),false);if(slot==null)return null;slot.setTaskId(taskId);slot.setLeaseToken(UUID.randomUUID().toString());slot.setLeaseUntil(now+leaseMillis);updateById(slot);return slot;}
    @Override @Transactional(rollbackFor = Exception.class)
    public void releaseByTask(String taskId){if(taskId==null)return;lambdaUpdate().eq(EvaluationWorkerSlot::getTaskId,taskId).set(EvaluationWorkerSlot::getTaskId,null).set(EvaluationWorkerSlot::getLeaseToken,null).set(EvaluationWorkerSlot::getLeaseUntil,null).update();}
}
