package io.choerodon.devops.app.service;

import java.util.List;

import io.choerodon.devops.infra.dto.DevopsCdStageRecordDTO;

/**
 * 〈功能简述〉
 * 〈〉
 *
 * @author wanghao
 * @since 2020/7/2 11:07
 */
public interface DevopsCdStageRecordService {
    /**
     * 保存流水线阶段记录
     * @param devopsCdStageRecordDTO
     */
    void save(DevopsCdStageRecordDTO devopsCdStageRecordDTO);


    List<DevopsCdStageRecordDTO> queryByPipelineRecordId(Long pipelineRecordId);

    DevopsCdStageRecordDTO queryFirstByPipelineRecordId(Long pipelineRecordId);

    void update(DevopsCdStageRecordDTO devopsCdStageRecord);


    void updateStatusById(Long stageRecordId, String status);

    DevopsCdStageRecordDTO queryById(Long id);

    void updateStageStatusFailed(Long stageRecordId);

    /**
     * 更新stage状态为待审核，同时更新流水线状态为待审核，以及通知审核人员
     *
     * @param pipelineRecordId
     * @param stageRecordId
     */
    void updateStageStatusNotAudit(Long pipelineRecordId, Long stageRecordId);

    void deleteByPipelineRecordId(Long pipelineRecordId);

    void updateStageStatusStop(Long stageRecordId);

    DevopsCdStageRecordDTO queryStageWithPipelineRecordIdAndStatus(Long pipelineRecordId, String status);
}
