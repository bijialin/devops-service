package io.choerodon.devops.infra.mapper;

import io.choerodon.devops.infra.dto.DevopsCiContentDTO;
import org.apache.ibatis.annotations.Param;

/**
 * 〈功能简述〉
 * 〈〉
 *
 * @author wanghao
 * @Date 2020/4/3 9:21
 */
public interface DevopsCiContentMapper extends BaseMapper<DevopsCiContentDTO> {

    String queryLatestContent(@Param("pipelineId") Long pipelineId);
}
