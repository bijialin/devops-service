package io.choerodon.devops.infra.feign;


import java.util.List;
import java.util.Set;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.choerodon.devops.api.vo.harbor.HarborCustomRepoVO;
import io.choerodon.devops.infra.dto.repo.NexusMavenRepoDTO;
import io.choerodon.devops.infra.feign.fallback.RdupmClientFallback;

/**
 * User: Mr.Wang
 * Date: 2020/6/11
 */
@FeignClient(value = "hrds-prod-repo", fallback = RdupmClientFallback.class)
public interface RdupmClient {

    /**
     * 查询项目下所有自定义仓库
     *
     * @param projectId
     * @return
     */
    @GetMapping("/v1/harbor-choerodon-repos/project/{projectId}/list_all_custom_repo")
    ResponseEntity<List<HarborCustomRepoVO>> listAllCustomRepoByProject(@PathVariable("projectId") Long projectId);


    /**
     * 查询关联的自定义仓库
     *
     * @param projectId
     * @param appServiceId
     * @return
     */
    @GetMapping("/v1/harbor-choerodon-repos/project/{projectId}/{appServiceId}/list_related_custom_repo")
    ResponseEntity<HarborCustomRepoVO> listRelatedCustomRepoByService(@ApiParam(value = "猪齿鱼项目ID", required = true)
                                                                      @PathVariable("projectId") Long projectId,
                                                                      @ApiParam(value = "应用服务ID", required = true)
                                                                      @PathVariable("appServiceId") Long appServiceId);

    /**
     * 保存关联关系
     *
     * @param projectId
     * @param appServiceId
     * @param customRepoId
     * @return
     */
    @PostMapping("/v1/harbor-choerodon-repos/project/{projectId}/{appServiceId}/save_relation")
    ResponseEntity saveRelationByService(@ApiParam(value = "猪齿鱼项目ID", required = true)
                                         @PathVariable("projectId") Long projectId,
                                         @ApiParam(value = "应用服务ID", required = true)
                                         @PathVariable("appServiceId") Long appServiceId,
                                         @ApiParam(value = "自定义仓库ID", required = true) @RequestParam("customRepoId") Long customRepoId);


    /**
     * 删除关联关系
     *
     * @param projectId
     * @param appServiceId
     * @param customRepoId
     * @return
     */
    @DeleteMapping("/v1/harbor-choerodon-repos/project/{projectId}/{appServiceId}/delete_relation")
    ResponseEntity deleteRelationByService(@ApiParam(value = "猪齿鱼项目ID", required = true)
                                           @PathVariable("projectId") Long projectId,
                                           @ApiParam(value = "应用服务ID", required = true)
                                           @PathVariable("appServiceId") Long appServiceId,
                                           @ApiParam(value = "自定义仓库ID", required = true)
                                           @RequestParam("customRepoId") Long customRepoId);

//    /**
//     * 仓库配置查询接口
//     * @param projectId
//     * @param appServiceId
//     * @return
//     */
//    @GetMapping("/v1/harbor-choerodon-repos/project/{projectId}/{appServiceId}/harbor_repo_config")
//    public ResponseEntity<HarborRepoDTO> queryHarborRepoConfig(@ApiParam(value = "猪齿鱼项目ID", required = true)
//                                                               @PathVariable("projectId") Long projectId,
//                                                               @ApiParam(value = "应用服务ID", required = true)
//                                                               @PathVariable("appServiceId") Long appServiceId);

    @ApiOperation(value = "CI-流水线-获取项目下仓库列表")
    @GetMapping("/v1/nexus-repositorys/{organizationId}/project/{projectId}/ci/repo/list")
    ResponseEntity<List<NexusMavenRepoDTO>> getRepoByProject(
            @ApiParam(value = "组织ID", required = true)
            @PathVariable(name = "organizationId") Long organizationId,
            @ApiParam(value = "项目Id", required = true)
            @PathVariable(name = "projectId") Long projectId,
            @ApiParam(value = "制品类型: MAVEN、NPM ", required = true)
            @RequestParam("repoType") String repoType,
            @ApiParam(value = "nexus仓库类型: hosted、proxy、group", required = false)
            @RequestParam(name = "type", required = false) String type);


    @ApiOperation(value = "CI-流水线-获取项目下仓库列表-包含用户信息")
    @GetMapping("/v1/nexus-repositorys/{organizationId}/project/{projectId}/ci/repo/user/list")
    ResponseEntity<List<NexusMavenRepoDTO>> getRepoUserByProject(
            @ApiParam(value = "组织ID", required = true)
            @PathVariable(name = "organizationId") Long organizationId,
            @ApiParam(value = "项目Id", required = true)
            @PathVariable(name = "projectId") Long projectId,
            @ApiParam(value = "仓库主键list", required = true)
            @RequestParam("repositoryIds") Set<Long> repositoryIds);
}
