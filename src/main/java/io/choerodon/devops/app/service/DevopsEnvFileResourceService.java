package io.choerodon.devops.app.service;

import java.util.List;
import java.util.Map;

import io.choerodon.devops.infra.dto.DevopsEnvFileResourceDTO;

public interface DevopsEnvFileResourceService {

    void updateOrCreateFileResource(Map<String, String> objectPath,
                                    Long envId,
                                    DevopsEnvFileResourceDTO devopsEnvFileResourceDTO,
                                    Integer i, Long id, String kind);

    DevopsEnvFileResourceDTO baseCreate(DevopsEnvFileResourceDTO devopsEnvFileResourceDTO);

    DevopsEnvFileResourceDTO baseQuery(Long fileResourceId);

    DevopsEnvFileResourceDTO baseUpdate(DevopsEnvFileResourceDTO devopsEnvFileResourceDTO);

    void baseDeleteById(Long fileResourceId);

    DevopsEnvFileResourceDTO baseQueryByEnvIdAndResourceId(Long envId, Long resourceId, String resourceType);

    List<DevopsEnvFileResourceDTO> baseQueryByEnvIdAndPath(Long envId, String path);

    void baseDeleteByEnvIdAndResourceId(Long envId, Long resourceId, String resourceType);

    /**
     * 根据环境id删除相应的纪录
     * 删除环境时使用
     *
     * @param envId 环境id
     */
    void deleteByEnvId(Long envId);
}
