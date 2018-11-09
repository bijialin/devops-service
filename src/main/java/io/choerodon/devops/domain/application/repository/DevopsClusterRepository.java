package io.choerodon.devops.domain.application.repository;

import java.util.List;

import io.choerodon.core.domain.Page;
import io.choerodon.devops.domain.application.entity.DevopsClusterE;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;

public interface DevopsClusterRepository {

    DevopsClusterE create(DevopsClusterE devopsClusterE);

    void checkName(DevopsClusterE devopsClusterE);

    void checkCode(DevopsClusterE devopsClusterE);

    List<DevopsClusterE> listByProjectId(Long projectId);

    DevopsClusterE query(Long clusterId);

    void update(DevopsClusterE devopsClusterE);

    Page<DevopsClusterE> pageClusters(Long organizationId, PageRequest pageRequest, String params);

    void delete(Long clusterId);
}
