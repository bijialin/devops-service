package io.choerodon.devops.app.service;

import java.util.List;

import io.choerodon.devops.infra.dto.DevopsCdJobRecordDTO;

/**
 * @author scp
 * @date 2020/7/3
 * @description
 */
public interface DevopsCdJobRecordService {

    List<DevopsCdJobRecordDTO> queryByStageRecordId(Long stageRecordId);


    /**
     * 保存cd job执行记录
     * @param devopsCdJobRecordDTO
     */
    void save(DevopsCdJobRecordDTO devopsCdJobRecordDTO);
}