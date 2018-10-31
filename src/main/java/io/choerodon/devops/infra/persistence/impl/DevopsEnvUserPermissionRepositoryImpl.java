package io.choerodon.devops.infra.persistence.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.choerodon.core.convertor.ConvertHelper;
import io.choerodon.core.convertor.ConvertPageHelper;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.devops.api.dto.DevopsEnvUserPermissionDTO;
import io.choerodon.devops.api.dto.iam.ProjectWithRoleDTO;
import io.choerodon.devops.api.dto.iam.RoleDTO;
import io.choerodon.devops.domain.application.entity.DevopsEnvUserPermissionE;
import io.choerodon.devops.domain.application.entity.DevopsEnvironmentE;
import io.choerodon.devops.domain.application.entity.ProjectE;
import io.choerodon.devops.domain.application.repository.DevopsEnvUserPermissionRepository;
import io.choerodon.devops.domain.application.repository.DevopsEnvironmentRepository;
import io.choerodon.devops.domain.application.repository.IamRepository;
import io.choerodon.devops.infra.common.util.TypeUtil;
import io.choerodon.devops.infra.dataobject.DevopsEnvUserPermissionDO;
import io.choerodon.devops.infra.mapper.DevopsEnvUserPermissionMapper;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;

/**
 * Created by n!Ck
 * Date: 2018/10/26
 * Time: 9:37
 * Description:
 */

@Service
public class DevopsEnvUserPermissionRepositoryImpl implements DevopsEnvUserPermissionRepository {

    private static final Gson gson = new Gson();
    private static final String PROJECT_OWNER = "role/project/default/project-owner";


    @Autowired
    private DevopsEnvUserPermissionMapper devopsEnvUserPermissionMapper;
    @Autowired
    private IamRepository iamRepository;
    @Autowired
    private DevopsEnvironmentRepository devopsEnvironmentRepository;

    @Override
    public void create(DevopsEnvUserPermissionE devopsEnvUserPermissionE) {
        DevopsEnvUserPermissionDO devopsEnvUserPermissionDO = ConvertHelper
                .convert(devopsEnvUserPermissionE, DevopsEnvUserPermissionDO.class);
        devopsEnvUserPermissionMapper.insert(devopsEnvUserPermissionDO);
    }

    @Override
    public Page<DevopsEnvUserPermissionDTO> pageUserPermissionByOption(Long envId, PageRequest pageRequest,
                                                                       String params) {
        Map maps = gson.fromJson(params, Map.class);
        Map<String, Object> searchParamMap = TypeUtil.cast(maps.get(TypeUtil.SEARCH_PARAM));
        String paramMap = TypeUtil.cast(maps.get(TypeUtil.PARAM));
        Page<DevopsEnvUserPermissionDTO> devopsEnvUserPermissionDTOPage = PageHelper.doPage(pageRequest.getPage(),
                pageRequest.getSize(), () -> devopsEnvUserPermissionMapper
                        .pageUserEnvPermissionByOption(envId, searchParamMap, paramMap));
        return ConvertPageHelper.convertPage(devopsEnvUserPermissionDTOPage, DevopsEnvUserPermissionDTO.class);
    }

    @Override
    public List<DevopsEnvUserPermissionDTO> listALlUserPermission(Long envId) {
        return ConvertHelper.convertList(devopsEnvUserPermissionMapper.listAllUserPermission(envId),
                DevopsEnvUserPermissionDTO.class);
    }

    @Override
    public List<DevopsEnvUserPermissionE> listAll(Long envId) {
        return ConvertHelper.convertList(devopsEnvUserPermissionMapper.listAll(envId), DevopsEnvUserPermissionE.class);
    }

    @Override
    public Integer updateEnvUserPermission(Long envId, List<Long> userIds) {
        devopsEnvUserPermissionMapper.initUserPermission(envId);
        return devopsEnvUserPermissionMapper.updateEnvUserPermission(envId, userIds);
    }

    @Override
    public List<DevopsEnvUserPermissionE> listByUserId(Long userId) {
        DevopsEnvUserPermissionDO devopsEnvUserPermissionDO = new DevopsEnvUserPermissionDO();
        devopsEnvUserPermissionDO.setIamUserId(userId);
        return ConvertHelper.convertList(devopsEnvUserPermissionMapper.select(devopsEnvUserPermissionDO), DevopsEnvUserPermissionE.class);
    }

    @Override
    public void checkEnvDeployPermission(Long userId, Long envId) {
        DevopsEnvironmentE devopsEnvironmentE = devopsEnvironmentRepository.queryById(envId);
        ProjectE projectE = iamRepository.queryIamProject(devopsEnvironmentE.getProjectE().getId());
        //判断当前用户是否是项目所有者，如果是，直接跳过校验，如果不是，校验环境权限
        if (!isProjectOwner(userId, projectE)) {
            DevopsEnvUserPermissionDO devopsEnvUserPermissionDO = new DevopsEnvUserPermissionDO();
            devopsEnvUserPermissionDO.setIamUserId(userId);
            devopsEnvUserPermissionDO.setEnvId(envId);
            devopsEnvUserPermissionDO = devopsEnvUserPermissionMapper.selectOne(devopsEnvUserPermissionDO);
            if (devopsEnvUserPermissionDO != null && !devopsEnvUserPermissionDO.getPermitted()) {
                throw new CommonException("error.env.user.permission.get");
            }
        }
    }

    @Override
    public boolean isProjectOwner(Long userId, ProjectE projectE) {
        List<ProjectWithRoleDTO> projectWithRoleDTOList = iamRepository.listProjectWithRoleDTO(userId);
        List<RoleDTO> roleDTOS = new ArrayList<>();
        projectWithRoleDTOList.stream().filter(projectWithRoleDTO -> projectWithRoleDTO.getName().equals(projectE.getName())).forEach(projectWithRoleDTO ->
            roleDTOS.addAll(projectWithRoleDTO.getRoles().stream().filter(roleDTO -> roleDTO.getCode().equals(PROJECT_OWNER)).collect(Collectors.toList())));
        if (roleDTOS.size() > 0) {
            return true;
        } else {
            return false;
        }
    }
}
