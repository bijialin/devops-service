package io.choerodon.devops.app.service.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import io.choerodon.base.domain.Sort;
import io.choerodon.devops.infra.dto.iam.ProjectDTO;
import io.choerodon.devops.infra.feign.operator.IamServiceClientOperator;
import io.choerodon.devops.infra.mapper.ApplicationShareMapper;
import io.choerodon.devops.infra.util.*;
import io.kubernetes.client.JSON;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import retrofit2.Response;

import io.choerodon.base.domain.PageRequest;
import io.choerodon.core.convertor.ConvertHelper;
import io.choerodon.core.convertor.ConvertPageHelper;
import io.choerodon.core.exception.CommonException;
import io.choerodon.devops.api.vo.AccessTokenCheckResultDTO;
import io.choerodon.devops.api.vo.AccessTokenDTO;
import io.choerodon.devops.api.vo.AppMarketDownloadDTO;
import io.choerodon.devops.api.vo.AppMarketTgzDTO;
import io.choerodon.devops.api.vo.AppMarketVersionDTO;
import io.choerodon.devops.api.vo.AppVersionAndValueDTO;
import io.choerodon.devops.api.vo.ApplicationReleasingDTO;
import io.choerodon.devops.api.vo.ApplicationVersionRemoteDTO;
import io.choerodon.devops.api.vo.ApplicationVersionRepDTO;
import io.choerodon.devops.api.vo.ProjectReqVO;
import io.choerodon.devops.app.service.ApplicationShareService;
import io.choerodon.devops.api.vo.iam.entity.AppShareResourceE;
import io.choerodon.devops.api.vo.iam.entity.ApplicationE;
import io.choerodon.devops.api.vo.iam.entity.ApplicationVersionE;
import io.choerodon.devops.api.vo.iam.entity.ApplicationVersionValueE;
import io.choerodon.devops.api.vo.iam.entity.DevopsAppShareE;
import io.choerodon.devops.api.vo.ProjectVO;
import io.choerodon.devops.domain.application.factory.ApplicationMarketFactory;
import io.choerodon.devops.domain.application.repository.DevopsProjectConfigRepository;
import io.choerodon.devops.domain.application.repository.MarketConnectInfoRepositpry;
import io.choerodon.devops.domain.application.valueobject.OrganizationVO;
import io.choerodon.devops.infra.config.HarborConfigurationProperties;
import io.choerodon.devops.infra.handler.RetrofitHandler;
import io.choerodon.devops.infra.dto.ApplicationShareVersionDTO;
import io.choerodon.devops.infra.dto.ApplicationShareDTO;
import io.choerodon.devops.infra.dto.DevopsMarketConnectInfoDTO;
import io.choerodon.devops.infra.feign.AppShareClient;
import io.choerodon.devops.infra.mapper.ApplicationVersionReadmeMapper;
import io.choerodon.websocket.tool.UUIDTool;

/**
 * Created by ernst on 2018/5/12.
 */
@Service
public class ApplicationShareServiceImpl implements ApplicationShareService {
    private static final String CHARTS = "charts";
    private static final String CHART = "chart";
    private static final String ORGANIZATION = "organization";
    private static final String PROJECTS = "projects";
    private static final String IMAGES = "images";
    private static final String PUSH_IAMGES = "push_image.sh";
    private static final String JSON_FILE = ".json";

    private static final String FILE_SEPARATOR = "/";
    private static final Logger logger = LoggerFactory.getLogger(ApplicationShareServiceImpl.class);

    private static Gson gson = new Gson();
    private JSON json = new JSON();

    @Value("${services.gitlab.url}")
    private String gitlabUrl;
    @Value("${services.helm.url}")
    private String helmUrl;

    @Autowired
    private ApplicationVersionRepository applicationVersionRepository;
    @Autowired
    private AppShareRepository appShareRepository;
    @Autowired
    private AppShareRecouceRepository appShareRecouceRepository;
    @Autowired
    private IamRepository iamRepository;
    @Autowired
    private ApplicationRepository applicationRepository;
    @Autowired
    private HarborConfigurationProperties harborConfigurationProperties;
    @Autowired
    private ApplicationVersionValueRepository applicationVersionValueRepository;
    @Autowired
    private DevopsProjectConfigRepository devopsProjectConfigRepository;
    @Autowired
    private ApplicationVersionReadmeMapper applicationVersionReadmeMapper;
    @Autowired
    private MarketConnectInfoRepositpry marketConnectInfoRepositpry;
    @Autowired
    private ChartUtil chartUtil;
    @Autowired
    private ApplicationShareMapper applicationShareMapper;
    @Autowired
    private IamServiceClientOperator iamServiceClientOperator;

    @Override
    public Long release(Long projectId, ApplicationReleasingDTO applicationReleasingDTO) {
        List<Long> ids;
        if (applicationReleasingDTO == null) {
            throw new CommonException("error.app.check");
        }
        String publishLevel = applicationReleasingDTO.getPublishLevel();
        if (!ORGANIZATION.equals(publishLevel) && !PROJECTS.equals(publishLevel)) {
            throw new CommonException("error.publishLevel");
        }
        DevopsAppShareE devopsAppShareE = ApplicationMarketFactory.create();
        //校验应用和版本
        if (projectId != null) {
            appShareRepository.baseCheckPub(applicationReleasingDTO.getAppId());
            List<AppMarketVersionDTO> appVersions = applicationReleasingDTO.getAppVersions();
            ids = appVersions.stream().map(AppMarketVersionDTO::getId)
                    .collect(Collectors.toCollection(ArrayList::new));
            applicationVersionRepository.baseCheckByAppIdAndVersionIds(applicationReleasingDTO.getAppId(), ids);
            applicationVersionRepository.baseUpdatePublishLevelByIds(ids, 1L);
            devopsAppShareE.initApplicationEById(applicationReleasingDTO.getAppId());
            devopsAppShareE.setPublishLevel(applicationReleasingDTO.getPublishLevel());
            devopsAppShareE.setActive(true);
            devopsAppShareE.setContributor(applicationReleasingDTO.getContributor());
            devopsAppShareE.setDescription(applicationReleasingDTO.getDescription());
            devopsAppShareE.setCategory(applicationReleasingDTO.getCategory());
            devopsAppShareE.setImgUrl(applicationReleasingDTO.getImgUrl());
            devopsAppShareE.setFree(applicationReleasingDTO.getFree());
        } else {
            devopsAppShareE.setId(applicationReleasingDTO.getId());
            devopsAppShareE.setSite(true);
        }
        devopsAppShareE = appShareRepository.baseCreateOrUpdate(devopsAppShareE);
        Long shareId = devopsAppShareE.getId();
        if (PROJECTS.equals(applicationReleasingDTO.getPublishLevel())) {
            applicationReleasingDTO.getProjectDTOS().forEach(t -> appShareRecouceRepository.baseCreate(new AppShareResourceE(shareId, t.getId())));
        }
        return appShareRepository.baseQueryByAppId(applicationReleasingDTO.getAppId());
    }

    @Override
    public PageInfo<ApplicationReleasingDTO> listMarketAppsByProjectId(Long projectId, PageRequest pageRequest,
                                                                       String searchParam) {
        PageInfo<ApplicationReleasingDTO> applicationMarketEPage = ConvertPageHelper.convertPageInfo(
                appShareRepository.basePageByProjectId(
                        projectId, pageRequest, searchParam),
                ApplicationReleasingDTO.class);
        List<ApplicationReleasingDTO> appShareEList = applicationMarketEPage.getList();
        appShareEList.forEach(t -> {
            if (PROJECTS.equals(t.getPublishLevel())) {
                List<ProjectReqVO> projectDTOS = appShareRecouceRepository.baseListByShareId(t.getId()).stream()
                        .map(appShareResourceE -> {
                            ProjectVO projectE = iamRepository.queryIamProject(appShareResourceE.getProjectId());
                            ProjectReqVO projectDTO = new ProjectReqVO();
                            BeanUtils.copyProperties(projectE, projectDTO);
                            return projectDTO;
                        })
                        .collect(Collectors.toList());
                t.setProjectDTOS(projectDTOS);
            }
        });
        applicationMarketEPage.setList(appShareEList);
        return applicationMarketEPage;
    }

    @Override
    public PageInfo<ApplicationReleasingDTO> listMarketAppsBySite(Boolean isSite, Boolean isFree, PageRequest pageRequest, String searchParam) {
        PageInfo<DevopsAppShareE> applicationMarketEPage = appShareRepository.basePageBySite(isSite, isFree, pageRequest, searchParam);
        return ConvertPageHelper.convertPageInfo(
                applicationMarketEPage,
                ApplicationReleasingDTO.class);
    }

    @Override
    public ApplicationReleasingDTO getAppDetailByShareId(Long shareId) {
        return getMarketAppInProject(null, shareId);
    }

    @Override
    public List<Long> batchRelease(List<ApplicationReleasingDTO> releasingDTOList) {
        return releasingDTOList.stream().map(releasingDTO -> release(null, releasingDTO)).collect(Collectors.toList());
    }

    @Override
    public PageInfo<ApplicationReleasingDTO> getAppsDetail(PageRequest pageRequest, String params, List<Long> shareIds) {
        try {
            params = params == null || params.isEmpty() ? params : URLDecoder.decode(params, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new CommonException("error.decode.params");
        }
        PageInfo<DevopsAppShareE> devopsAppShareEPageInfo = appShareRepository.basePageByShareIds(pageRequest, params, shareIds);
        return ConvertPageHelper.convertPageInfo(devopsAppShareEPageInfo, ApplicationReleasingDTO.class);
    }

    @Override
    public PageInfo<ApplicationVersionRepDTO> getVersionsByAppId(Long appId, PageRequest pageRequest, String params) {
        PageInfo<ApplicationVersionE> applicationVersionEPageInfo = applicationVersionRepository.listByAppIdAndParamWithPage(appId, true, null, pageRequest, params);
        if (applicationVersionEPageInfo.getList() == null) {
            return new PageInfo<>();
        }
        return ConvertPageHelper.convertPageInfo(applicationVersionEPageInfo, ApplicationVersionRepDTO.class);
    }

    @Override
    public AppVersionAndValueDTO getValuesAndChart(Long versionId) {
        AppVersionAndValueDTO appVersionAndValueDTO = new AppVersionAndValueDTO();
        String versionValue = FileUtil.checkValueFormat(applicationVersionRepository.baseQueryValue(versionId));
        ApplicationVersionRemoteDTO versionRemoteDTO = new ApplicationVersionRemoteDTO();
        versionRemoteDTO.setValues(versionValue);
        ApplicationVersionE applicationVersionE = applicationVersionRepository.baseQuery(versionId);
        if (applicationVersionE != null) {
            versionRemoteDTO.setRepository(applicationVersionE.getRepository());
            versionRemoteDTO.setVersion(applicationVersionE.getVersion());
            versionRemoteDTO.setImage(applicationVersionE.getImage());
            versionRemoteDTO.setReadMeValue(applicationVersionReadmeMapper.selectByPrimaryKey(applicationVersionE.getApplicationVersionReadmeV().getId()).getReadme());
            ApplicationE applicationE = applicationRepository.query(applicationVersionE.getApplicationE().getId());
            if (applicationE.getHarborConfigE() == null) {
                appVersionAndValueDTO.setHarbor(devopsProjectConfigRepository.baseQueryByName(null, "harbor_default").getConfig());
                appVersionAndValueDTO.setChart(devopsProjectConfigRepository.baseQueryByName(null, "chart_default").getConfig());
            } else {
                appVersionAndValueDTO.setHarbor(devopsProjectConfigRepository.baseQuery(applicationE.getHarborConfigE().getId()).getConfig());
                appVersionAndValueDTO.setChart(devopsProjectConfigRepository.baseQuery(applicationE.getChartConfigE().getId()).getConfig());
            }
            appVersionAndValueDTO.setVersionRemoteDTO(versionRemoteDTO);
        }
        return appVersionAndValueDTO;
    }

    @Override
    public void updateByShareId(Long shareId, Boolean isFree) {
        ApplicationShareDTO applicationShareDTO = new ApplicationShareDTO();
        applicationShareDTO.setId(shareId);
        applicationShareDTO.setFree(isFree);
        appShareRepository.baseUpdate(applicationShareDTO);
    }

    @Override
    public PageInfo<ApplicationReleasingDTO> listMarketApps(Long projectId, PageRequest pageRequest, String searchParam) {
        ProjectVO projectE = iamRepository.queryIamProject(projectId);
        if (projectE != null && projectE.getOrganization() != null) {
            Long organizationId = projectE.getOrganization().getId();
            List<ProjectVO> projectEList = iamRepository.listIamProjectByOrgId(organizationId, null, null);
            List<Long> projectIds = new ArrayList<>();
            if (projectEList != null) {
                projectIds = projectEList.stream().map(ProjectVO::getId).collect(Collectors.toList());
            }
            PageInfo<DevopsAppShareE> applicationMarketEPage = appShareRepository.basePageByProjectIds(
                    projectIds, pageRequest, searchParam);

            return ConvertPageHelper.convertPageInfo(
                    applicationMarketEPage,
                    ApplicationReleasingDTO.class);
        }
        return null;
    }

    @Override
    public ApplicationReleasingDTO getMarketAppInProject(Long projectId, Long appMarketId) {
        DevopsAppShareE applicationMarketE =
                appShareRepository.baseQuery(projectId, appMarketId);
        List<ApplicationShareVersionDTO> versionDOList = appShareRepository
                .pageByOptions(projectId, appMarketId, true);
        List<AppMarketVersionDTO> appMarketVersionDTOList = ConvertHelper
                .convertList(versionDOList, AppMarketVersionDTO.class);
        ApplicationReleasingDTO applicationReleasingDTO =
                ConvertHelper.convert(applicationMarketE, ApplicationReleasingDTO.class);
        applicationReleasingDTO.setAppVersions(appMarketVersionDTOList);

        return applicationReleasingDTO;
    }

    @Override
    public ApplicationReleasingDTO getMarketApp(Long appMarketId, Long versionId) {
        DevopsAppShareE applicationMarketE =
                appShareRepository.baseQuery(null, appMarketId);
        ApplicationE applicationE = applicationMarketE.getApplicationE();
        List<ApplicationShareVersionDTO> versionDOList = appShareRepository
                .pageByOptions(null, appMarketId, true);
        List<AppMarketVersionDTO> appMarketVersionDTOList = ConvertHelper
                .convertList(versionDOList, AppMarketVersionDTO.class)
                .stream()
                .sorted(this::compareAppMarketVersionDTO)
                .collect(Collectors.toCollection(ArrayList::new));
        ApplicationReleasingDTO applicationReleasingDTO =
                ConvertHelper.convert(applicationMarketE, ApplicationReleasingDTO.class);
        applicationReleasingDTO.setAppVersions(appMarketVersionDTOList);

        Long applicationId = applicationE.getId();
        applicationE = applicationRepository.query(applicationId);

        Date latestUpdateDate = appMarketVersionDTOList.isEmpty()
                ? getLaterDate(applicationE.getLastUpdateDate(), applicationMarketE.getMarketUpdatedDate())
                : getLatestDate(
                appMarketVersionDTOList.get(0).getUpdatedDate(),
                applicationE.getLastUpdateDate(),
                applicationMarketE.getMarketUpdatedDate());
        applicationReleasingDTO.setLastUpdatedDate(latestUpdateDate);

        Boolean versionExist = appMarketVersionDTOList.stream().anyMatch(t -> t.getId().equals(versionId));
        Long latestVersionId = versionId;
        if (!versionExist) {
            Optional<AppMarketVersionDTO> optional = appMarketVersionDTOList.stream()
                    .max(this::compareAppMarketVersionDTO);
            latestVersionId = optional.isPresent()
                    ? optional.get().getId()
                    : versionId;
        }
        ApplicationVersionE applicationVersionE = applicationVersionRepository.baseQuery(latestVersionId);
        String readme = applicationVersionRepository
                .baseQueryReadme(applicationVersionE.getApplicationVersionReadmeV().getId());

        applicationReleasingDTO.setReadme(readme);

        return applicationReleasingDTO;
    }

    private Date getLatestDate(Date a, Date b, Date c) {
        if (a.after(b)) {
            return getLaterDate(a, c);
        } else {
            return getLaterDate(b, c);
        }
    }

    private Date getLaterDate(Date a, Date b) {
        return a.after(b) ? a : b;
    }

    private Integer compareAppMarketVersionDTO(AppMarketVersionDTO s, AppMarketVersionDTO t) {
        if (s.getUpdatedDate().before(t.getUpdatedDate())) {
            return 1;
        } else {
            if (s.getUpdatedDate().after(t.getUpdatedDate())) {
                return -1;
            } else {
                if (s.getCreationDate().before(t.getCreationDate())) {
                    return 1;
                } else {
                    return s.getCreationDate().after(t.getCreationDate()) ? -1 : 0;
                }
            }
        }
    }

    @Override
    public String getMarketAppVersionReadme(Long appMarketId, Long versionId) {
        appShareRepository.baseCheckByMarketIdAndVersion(appMarketId, versionId);
        ApplicationVersionE applicationVersionE = applicationVersionRepository.baseQuery(versionId);
        return applicationVersionRepository.baseQueryReadme(applicationVersionE.getApplicationVersionReadmeV().getId());
    }

    @Override
    public void unpublish(Long projectId, Long appMarketId) {
        appShareRepository.baseCheckByProjectId(projectId, appMarketId);
        appShareRepository.baseCheckByDeployed(projectId, appMarketId, null, null);
        appShareRepository.baseUnsharedApplication(appMarketId);
    }

    @Override
    public void unpublish(Long projectId, Long appMarketId, Long versionId) {
        appShareRepository.baseCheckByProjectId(projectId, appMarketId);
        appShareRepository.baseCheckByDeployed(projectId, appMarketId, versionId, null);
        appShareRepository.baseUnsharedApplicationVersion(appMarketId, versionId);

    }

    @Override
    public void update(Long projectId, Long appMarketId, ApplicationReleasingDTO applicationRelease) {
        if (applicationRelease != null) {
            String publishLevel = applicationRelease.getPublishLevel();
            if (publishLevel != null
                    && !ORGANIZATION.equals(publishLevel)
                    && !PROJECTS.equals(publishLevel)) {
                throw new CommonException("error.publishLevel");
            }
        } else {
            throw new CommonException("error.app.check");
        }
        if (applicationRelease.getId() != null
                && !appMarketId.equals(applicationRelease.getId())) {
            throw new CommonException("error.id.notMatch");
        }
        appShareRepository.baseCheckByProjectId(projectId, appMarketId);
        ApplicationReleasingDTO applicationReleasingDTO = getMarketAppInProject(projectId, appMarketId);
        if (applicationRelease.getAppId() != null
                && !applicationReleasingDTO.getAppId().equals(applicationRelease.getAppId())) {
            throw new CommonException("error.app.cannot.change");
        }
        ProjectVO projectE = iamRepository.queryIamProject(projectId);
        if (projectE == null || projectE.getOrganization() == null) {
            throw new CommonException("error.project.query");
        }
        if (applicationRelease.getPublishLevel() != null
                && !applicationRelease.getPublishLevel().equals(applicationReleasingDTO.getPublishLevel())) {
            throw new CommonException("error.publishLevel.cannot.change");
        }
        ApplicationShareDTO devopsAppMarketDO = ConvertHelper.convert(applicationRelease, ApplicationShareDTO.class);
        if (!ConvertHelper.convert(applicationReleasingDTO, ApplicationShareDTO.class).equals(devopsAppMarketDO)) {
            appShareRepository.baseUpdate(devopsAppMarketDO);
        }
    }

    @Override
    public void update(Long projectId, Long appMarketId, List<AppMarketVersionDTO> versionDTOList) {
        appShareRepository.baseCheckByProjectId(projectId, appMarketId);

        ApplicationReleasingDTO applicationReleasingDTO = getMarketAppInProject(projectId, appMarketId);

        List<Long> ids = versionDTOList.stream()
                .map(AppMarketVersionDTO::getId).collect(Collectors.toCollection(ArrayList::new));

        applicationVersionRepository.baseCheckByAppIdAndVersionIds(applicationReleasingDTO.getAppId(), ids);
        applicationVersionRepository.baseUpdatePublishLevelByIds(ids, 1L);
    }

    @Override
    public List<AppMarketVersionDTO> getAppVersions(Long projectId, Long appMarketId, Boolean isPublish) {
        return ConvertHelper.convertList(appShareRepository.pageByOptions(projectId, appMarketId, isPublish),
                AppMarketVersionDTO.class);
    }

    @Override
    public PageInfo<AppMarketVersionDTO> getAppVersions(Long projectId, Long appMarketId, Boolean isPublish,
                                                        PageRequest pageRequest, String searchParam) {
        return ConvertPageHelper.convertPageInfo(
                appShareRepository.pageByOptions(projectId, appMarketId, isPublish, pageRequest, searchParam),
                AppMarketVersionDTO.class);
    }

    @Override
    public AppMarketTgzDTO getMarketAppListInFile(Long projectId, MultipartFile file) {
        ProjectVO projectE = iamRepository.queryIamProject(projectId);
        OrganizationVO organization = iamRepository.queryOrganizationById(projectE.getOrganization().getId());
        String dirName = UUIDTool.genUuid();
        String classPath = String.format(
                "tmp%s%s%s%s",
                FILE_SEPARATOR,
                organization.getCode(),
                FILE_SEPARATOR,
                projectE.getCode());

        String destPath = String.format("%s%s%s", classPath, FILE_SEPARATOR, dirName);
        String path = FileUtil.multipartFileToFileWithSuffix(destPath, file, ".zip");
        FileUtil.unZipFiles(new File(path), destPath);
        FileUtil.deleteFile(path);
        File zipDirectory = new File(destPath);
        AppMarketTgzDTO appMarketTgzDTO = new AppMarketTgzDTO();

        if (zipDirectory.exists() && zipDirectory.isDirectory()) {
            File[] chartsDirectory = zipDirectory.listFiles();
            if (chartsDirectory != null && chartsDirectory.length == 1) {
                File[] appFiles = chartsDirectory[0].listFiles();
                if (appFiles == null || appFiles.length == 0) {
                    FileUtil.deleteDirectory(zipDirectory);
                    throw new CommonException("error.file.empty");
                }

                List<File> appFileList = Arrays.stream(appFiles)
                        .filter(File::isDirectory).collect(Collectors.toCollection(ArrayList::new));
                // do sth with appFileList
                analyzeAppFile(appMarketTgzDTO.getAppMarketList(), appFileList);
            } else {
                FileUtil.deleteDirectory(zipDirectory);
                throw new CommonException("error.zip.illegal");
            }
        } else {
            FileUtil.deleteDirectory(zipDirectory);
            throw new CommonException("error.zip.empty");
        }
        appMarketTgzDTO.setFileCode(dirName);
        return appMarketTgzDTO;
    }

    @Override
    public Boolean importApps(Long projectId, String fileName, Boolean isPublic) {
        ProjectVO projectE = iamRepository.queryIamProject(projectId);
        OrganizationVO organization = iamRepository.queryOrganizationById(projectE.getOrganization().getId());
        String destPath = String.format(
                "tmp%s%s%s%s%s%s",
                FILE_SEPARATOR,
                organization.getCode(),
                FILE_SEPARATOR,
                projectE.getCode(),
                FILE_SEPARATOR,
                fileName);
        File zipDirectory = new File(destPath);

        if (zipDirectory.exists() && zipDirectory.isDirectory()) {
            File[] chartsDirectory = zipDirectory.listFiles();
            File[] appFiles = chartsDirectory != null ? chartsDirectory[0].listFiles() : new File[0];
            if (appFiles == null || appFiles.length == 0) {
                FileUtil.deleteDirectory(zipDirectory);
                throw new CommonException("error.file.empty");
            }
            List<File> appFileList = Arrays.stream(appFiles)
                    .filter(File::isDirectory).collect(Collectors.toCollection(ArrayList::new));
            importAppFile(projectId, appFileList, isPublic);

        } else {
            throw new CommonException("error.zip.notFound");
        }
        FileUtil.deleteDirectory(zipDirectory);
        return true;
    }

    @Override
    public void deleteZip(Long projectId, String fileName) {
        ProjectVO projectE = iamRepository.queryIamProject(projectId);
        OrganizationVO organization = iamRepository.queryOrganizationById(projectE.getOrganization().getId());
        String destPath = String.format(
                "tmp%s%s%s%s%s%s",
                FILE_SEPARATOR,
                organization.getCode(),
                FILE_SEPARATOR,
                projectE.getCode(),
                FILE_SEPARATOR,
                fileName);
        File zipDirectory = new File(destPath);
        FileUtil.deleteDirectory(zipDirectory);
    }


    @Override
    public PageInfo<ApplicationReleasingDTO> pageListRemoteApps(Long projectId, PageRequest pageRequest, String params) {
        DevopsMarketConnectInfoDTO marketConnectInfoDO = marketConnectInfoRepositpry.baseQuery();
        if (marketConnectInfoDO == null) {
            throw new CommonException("not.exist.remote token");
        }
        AppShareClient shareClient = RetrofitHandler.getAppShareClient(marketConnectInfoDO.getSaasMarketUrl());
        Map<String, Object> map = new HashMap<>();
        map.put("page", pageRequest.getPage());
        map.put("size", pageRequest.getSize());
        map.put("sort", PageRequestUtil.getOrderByStr(pageRequest));
        map.put("access_token", marketConnectInfoDO.getAccessToken());
        Response<PageInfo<ApplicationReleasingDTO>> pageInfoResponse = null;
        try {
            map.put("params", URLEncoder.encode(params, "UTF-8"));
            pageInfoResponse = shareClient.getAppShares(map).execute();
            if (!pageInfoResponse.isSuccessful()) {
                throw new CommonException("error.get.app.shares");
            }
        } catch (IOException e) {
            throw new CommonException("error.get.app.shares");
        }
        return pageInfoResponse.body();
    }

    @Override
    public PageInfo<ApplicationVersionRepDTO> listVersionByAppId(Long appId, String accessToken, PageRequest pageRequest, String params) {
        DevopsMarketConnectInfoDTO marketConnectInfoDO = marketConnectInfoRepositpry.baseQuery();
        if (marketConnectInfoDO == null) {
            throw new CommonException("not.exist.remote token");
        }
        AppShareClient shareClient = RetrofitHandler.getAppShareClient(marketConnectInfoDO.getSaasMarketUrl());
        Map<String, Object> map = new HashMap<>();
        map.put("page", pageRequest.getPage());
        map.put("size", pageRequest.getSize());
        map.put("sort", PageRequestUtil.getOrderByStr(pageRequest));
        if (params != null) {
            map.put("params", params);
        }
        map.put("access_token", accessToken);
        Response<PageInfo<ApplicationVersionRepDTO>> pageInfoResponse = null;
        try {
            pageInfoResponse = shareClient.listVersionByAppId(appId, map).execute();
            if (!pageInfoResponse.isSuccessful()) {
                throw new CommonException("error.get.app.shares");
            }
        } catch (IOException e) {
            throw new CommonException("error.get.app.shares");
        }
        return pageInfoResponse.body();
    }

    @Override
    public AppVersionAndValueDTO getConfigInfoByVerionId(Long appId, Long versionId, String accessToken) {
        DevopsMarketConnectInfoDTO marketConnectInfoDO = marketConnectInfoRepositpry.baseQuery();
        if (marketConnectInfoDO == null) {
            throw new CommonException("not.exist.remote token");
        }
        AppShareClient shareClient = RetrofitHandler.getAppShareClient(marketConnectInfoDO.getSaasMarketUrl());
        Map<String, Object> map = new HashMap<>();
        map.put("access_token", accessToken);
        Response<AppVersionAndValueDTO> versionAndValueDTOResponse = null;
        try {
            versionAndValueDTOResponse = shareClient.getConfigInfoByVerionId(appId, versionId, map).execute();
            if (!versionAndValueDTOResponse.isSuccessful()) {
                throw new CommonException("error.get.app.shares");
            }
        } catch (IOException e) {
            throw new CommonException("error.get.app.shares");
        }
        return versionAndValueDTOResponse.body();
    }

    private void analyzeAppFile(List<ApplicationReleasingDTO> appMarketVersionDTOS,
                                List<File> appFileList) {
        appFileList.forEach(t -> {
            String appName = t.getName();
            File[] appFiles = t.listFiles();
            if (appFiles != null && !appFileList.isEmpty()) {
                String appFileName = String.format("%s%s", appName, JSON_FILE);
                List<File> appMarkets = Arrays.stream(appFiles).parallel()
                        .filter(k -> k.getName().equals(appFileName))
                        .collect(Collectors.toCollection(ArrayList::new));
                if (!appMarkets.isEmpty() && appMarkets.size() == 1) {
                    File appMarket = appMarkets.get(0);
                    String appMarketJson = FileUtil.getFileContent(appMarket);
                    ApplicationReleasingDTO appMarketVersionDTO =
                            gson.fromJson(appMarketJson, ApplicationReleasingDTO.class);
                    appMarketVersionDTOS.add(appMarketVersionDTO);
                }
            }
        });
    }

    private void importAppFile(Long projectId, List<File> appFileList, Boolean isPublic) {
        ProjectVO projectE = iamRepository.queryIamProject(projectId);
        OrganizationVO organization = iamRepository.queryOrganizationById(projectE.getOrganization().getId());
        String orgCode = organization.getCode();
        String projectCode = projectE.getCode();
        appFileList.stream().forEach(t -> {
            String appName = t.getName();
            File[] appFiles = t.listFiles();
            if (appFiles != null && !appFileList.isEmpty()) {
                String appFileName = String.format("%s%s", appName, JSON_FILE);
                List<File> appMarkets = Arrays.stream(appFiles).parallel()
                        .filter(k -> k.getName().equals(appFileName))
                        .collect(Collectors.toCollection(ArrayList::new));
                if (appMarkets != null && !appMarkets.isEmpty() && appMarkets.size() == 1) {
                    File appMarket = appMarkets.get(0);
                    String appMarketJson = FileUtil.getFileContent(appMarket);
                    ApplicationReleasingDTO applicationReleasingDTO =
                            gson.fromJson(appMarketJson, ApplicationReleasingDTO.class);
                    ApplicationE applicationE = new ApplicationE();
                    String appCode = applicationReleasingDTO.getCode();
                    applicationE.setName(applicationReleasingDTO.getName());
                    applicationE.setIsSkipCheckPermission(true);
                    applicationE.setType("normal");
                    Long appId = createOrUpdateApp(applicationE, appCode, projectId);
                    Boolean isVersionPublish = isPublic != null;
                    applicationReleasingDTO.getAppVersions().stream()
                            .forEach(appVersion -> createVersion(
                                    appVersion, orgCode, projectCode, appCode, appId, appFiles, isVersionPublish
                            ));
                    // 发布应用
                    releaseApp(isPublic, applicationReleasingDTO, appId);
                }
            }
        });
    }

    /**
     * 导出应用市场应用 zip
     *
     * @param appMarkets 应用市场应用信息
     */
    public void export(List<AppMarketDownloadDTO> appMarkets, String fileName) {
        List<String> images = new ArrayList<>();
        for (AppMarketDownloadDTO appMarketDownloadDTO : appMarkets) {
            ApplicationReleasingDTO applicationReleasingDTO = getMarketApp(appMarketDownloadDTO.getAppMarketId(), null);
            String destpath = String.format("charts%s%s",
                    FILE_SEPARATOR,
                    applicationReleasingDTO.getCode());
            ApplicationE applicationE = applicationRepository.query(applicationReleasingDTO.getAppId());
            ProjectVO projectE = iamRepository.queryIamProject(applicationE.getProjectE().getId());
            OrganizationVO organization = iamRepository.queryOrganizationById(projectE.getOrganization().getId());
            applicationReleasingDTO.setAppVersions(
                    applicationReleasingDTO.getAppVersions().stream()
                            .filter(t -> appMarketDownloadDTO.getAppVersionIds().contains(t.getId()))
                            .collect(Collectors.toCollection(ArrayList::new))
            );
            String appMarketJson = gson.toJson(applicationReleasingDTO);
            FileUtil.saveDataToFile(destpath, applicationReleasingDTO.getCode() + JSON_FILE, appMarketJson);
            //下载chart taz包
            getChart(images, appMarketDownloadDTO, destpath, applicationE, projectE, organization);
            StringBuilder stringBuilder = new StringBuilder();
            for (String image : images) {
                stringBuilder.append(image);
                stringBuilder.append(System.getProperty("line.separator"));
            }
            InputStream inputStream = this.getClass().getResourceAsStream("/shell/push_image.sh");
            FileUtil.saveDataToFile(fileName, PUSH_IAMGES, FileUtil.replaceReturnString(inputStream, null));
            FileUtil.saveDataToFile(fileName, IMAGES, stringBuilder.toString());
            FileUtil.moveFiles(CHARTS, fileName);
        }
        try (FileOutputStream outputStream = new FileOutputStream(fileName + ".zip")) {
            FileUtil.toZip(fileName, outputStream, true);
            FileUtil.deleteDirectory(new File(CHARTS));
            FileUtil.deleteDirectory(new File(fileName));
        } catch (IOException e) {
            throw new CommonException(e.getMessage(), e);
        }
    }

    private void getChart(List<String> images, AppMarketDownloadDTO appMarketDownloadDTO, String destpath, ApplicationE applicationE, ProjectVO projectE, OrganizationVO organization) {
        appMarketDownloadDTO.getAppVersionIds().forEach(appVersionId -> {

            ApplicationVersionE applicationVersionE = applicationVersionRepository.baseQuery(appVersionId);
            images.add(applicationVersionE.getImage());
            chartUtil.downloadChart(applicationVersionE, organization, projectE, applicationE, destpath);
        });
    }

    private void createVersion(AppMarketVersionDTO appVersion,
                               String organizationCode,
                               String projectCode,
                               String appCode,
                               Long appId,
                               File[] appFiles,
                               Boolean isVersionPublish) {
        ApplicationVersionE applicationVersionE = new ApplicationVersionE();
        String image = String.format("%s%s%s%s%s%s%s%s%s", harborConfigurationProperties.getBaseUrl(),
                FILE_SEPARATOR,
                organizationCode,
                "-",
                projectCode,
                FILE_SEPARATOR,
                appCode,
                ":",
                appVersion.getVersion()
        );
        applicationVersionE.setImage(image);
        helmUrl = helmUrl.endsWith("/") ? helmUrl : helmUrl + "/";
        applicationVersionE.setRepository(String.format("%s%s%s%s%s",
                helmUrl,
                organizationCode,
                FILE_SEPARATOR,
                projectCode,
                FILE_SEPARATOR));
        applicationVersionE.setVersion(appVersion.getVersion());
        applicationVersionE.initApplicationEById(appId);
        String tazName = String.format("%s%s%s%s",
                appCode,
                "-",
                appVersion.getVersion(),
                ".tgz"
        );
        List<File> tgzVersions = Arrays.stream(appFiles).parallel()
                .filter(k -> k.getName().equals(tazName))
                .collect(Collectors.toCollection(ArrayList::new));
        if (!tgzVersions.isEmpty()) {
            ApplicationVersionValueE applicationVersionValueE = new ApplicationVersionValueE();
            try {
                FileUtil.unTarGZ(tgzVersions.get(0).getAbsolutePath(), appCode);
                File valueYaml = FileUtil.queryFileFromFiles(new File(appCode), "values.yaml");
                if (valueYaml == null) {
                    throw new CommonException("error.version.values.notExist");
                }
                applicationVersionValueE.setValue(FileUtil.replaceReturnString(new FileInputStream(valueYaml), null));

                applicationVersionE.initApplicationVersionValueE(applicationVersionValueRepository
                        .baseCreate(applicationVersionValueE).getId());
            } catch (Exception e) {
                throw new CommonException("error.version.insert");
            }
            applicationVersionE.initApplicationVersionReadmeV(FileUtil.getReadme(appCode));
            ApplicationVersionE version = applicationVersionRepository
                    .baseQueryByAppIdAndVersion(appId, appVersion.getVersion());

            if (isVersionPublish) {
                applicationVersionE.setIsPublish(1L);
            } else {
                applicationVersionE.setIsPublish(version == null ? null : version.getIsPublish());
            }
            if (version == null) {
                applicationVersionRepository.baseCreate(applicationVersionE);
            } else {
                applicationVersionE.setId(version.getId());
                applicationVersionRepository.baseUpdate(applicationVersionE);
            }
            String classPath = String.format("Charts%s%s%s%s",
                    FILE_SEPARATOR,
                    organizationCode,
                    FILE_SEPARATOR,
                    projectCode);
            FileUtil.copyFile(tgzVersions.get(0).getAbsolutePath(), classPath);
            //上传tgz包到chart仓库
            chartUtil.uploadChart(organizationCode, projectCode, tgzVersions.get(0));
            FileUtil.deleteDirectory(new File(appCode));
        }
    }


    private Long createOrUpdateApp(ApplicationE applicationE, String appCode, Long projectId) {
        applicationE.setCode(appCode);
        applicationE.initProjectE(projectId);
        Long appId;
        Boolean appCodeExist = false;
        try {
            applicationRepository.checkCode(applicationE);
        } catch (Exception e) {
            logger.info(e.getMessage());
            appCodeExist = true;
        }
        if (!appCodeExist) {
            applicationE.setActive(true);
            applicationE.setSynchro(true);
            applicationE.setToken(GenerateUUID.generateUUID());
            appId = applicationRepository.create(applicationE).getId();
        } else {
            ApplicationE existApplication = applicationRepository.queryByCode(appCode, projectId);
            appId = existApplication.getId();
            applicationE.setId(appId);
            applicationRepository.update(applicationE);
        }
        return appId;
    }

    private Boolean checkAppCanPub(Long appId) {
        try {
            return appShareRepository.baseCheckPub(appId);
        } catch (Exception e) {
            return false;
        }
    }

    private void releaseApp(Boolean isPublic,
                            ApplicationReleasingDTO applicationReleasingDTO, Long appId) {
        if (isPublic != null) {
            Boolean canPub = checkAppCanPub(appId);
            if (canPub) {
                DevopsAppShareE applicationMarketE = new DevopsAppShareE();
                applicationMarketE.initApplicationEById(appId);
                applicationMarketE.setPublishLevel(isPublic ? PROJECTS : ORGANIZATION);
                applicationMarketE.setActive(true);
                applicationMarketE.setContributor(applicationReleasingDTO.getContributor());
                applicationMarketE.setDescription(applicationReleasingDTO.getDescription());
                applicationMarketE.setCategory(applicationReleasingDTO.getCategory());
                appShareRepository.baseCreateOrUpdate(applicationMarketE);
            }
        }
    }

    @Override
    public AccessTokenCheckResultDTO checkToken(AccessTokenDTO tokenDTO) {
        AppShareClient appShareClient = RetrofitHandler.getAppShareClient(tokenDTO.getSaasMarketUrl());
        Response<AccessTokenCheckResultDTO> tokenDTOResponse = null;

        try {
            tokenDTOResponse = appShareClient.checkTokenExist(tokenDTO.getAccessToken()).execute();
            if (!tokenDTOResponse.isSuccessful()) {
                throw new CommonException("error.check.token");
            }
        } catch (IOException e) {
            throw new CommonException("error.check.token");
        }
        return tokenDTOResponse.body();
    }

    @Override
    public void saveToken(AccessTokenDTO tokenDTO) {
        DevopsMarketConnectInfoDTO connectInfoDO = new DevopsMarketConnectInfoDTO();
        BeanUtils.copyProperties(tokenDTO, connectInfoDO);
        marketConnectInfoRepositpry.baseCreateOrUpdate(connectInfoDO);
    }

    public ApplicationShareDTO baseCreateOrUpdate(ApplicationShareDTO applicationShareDTO) {
        if (applicationShareDTO.getId() == null) {
            applicationShareMapper.insert(applicationShareDTO);
        } else {
            applicationShareDTO.setObjectVersionNumber(applicationShareMapper.selectByPrimaryKey(applicationShareDTO).getObjectVersionNumber());
            applicationShareMapper.updateByPrimaryKeySelective(applicationShareDTO);
        }
        return applicationShareDTO;
    }

    public PageInfo<ApplicationShareDTO> basePageByProjectId(Long projectId, PageRequest pageRequest, String searchParam) {
        PageInfo<ApplicationShareDTO> applicationShareDTOPageInfo;
        if (!StringUtils.isEmpty(searchParam)) {
            Map<String, Object> searchParamMap = json.deserialize(searchParam, Map.class);
            applicationShareDTOPageInfo = PageHelper.startPage(
                    pageRequest.getPage(), pageRequest.getSize(), PageRequestUtil.getOrderBy(pageRequest)).doSelectPageInfo(() -> applicationShareMapper.listMarketApplicationInProject(
                    projectId,
                    TypeUtil.cast(searchParamMap.get(TypeUtil.SEARCH_PARAM)),
                    TypeUtil.cast(searchParamMap.get(TypeUtil.PARAM))));
        } else {
            applicationShareDTOPageInfo = PageHelper.startPage(
                    pageRequest.getPage(), pageRequest.getSize(), PageRequestUtil.getOrderBy(pageRequest)).doSelectPageInfo(() -> applicationShareMapper.listMarketApplicationInProject(projectId, null, null));
        }
        return applicationShareDTOPageInfo;
    }

    public PageInfo<ApplicationShareDTO> basePageBySite(Boolean isSite, Boolean isFree, PageRequest pageRequest, String searchParam) {

        Map<String, Object> mapParams = TypeUtil.castMapParams(searchParam);

        PageInfo<ApplicationShareDTO> applicationShareDTOPageInfo = PageHelper
                .startPage(pageRequest.getPage(), pageRequest.getSize(), PageRequestUtil.getOrderBy(pageRequest)).doSelectPageInfo(() ->
                        applicationShareMapper.listMarketAppsBySite(isSite, isFree, (Map<String, Object>) mapParams.get(TypeUtil.SEARCH_PARAM), (String) mapParams.get(TypeUtil.PARAM)));
        return applicationShareDTOPageInfo;
    }


    public PageInfo<ApplicationShareDTO> basePageByProjectIds(List<Long> projectIds, PageRequest pageRequest, String searchParam) {
        PageInfo<ApplicationShareDTO> applicationShareDTOPageInfo;
        if (!StringUtils.isEmpty(searchParam)) {
            Map<String, Object> searchParamMap = json.deserialize(searchParam, Map.class);
            applicationShareDTOPageInfo = PageHelper.startPage(
                    pageRequest.getPage(), pageRequest.getSize(), PageRequestUtil.getOrderBy(pageRequest)).doSelectPageInfo(() -> applicationShareMapper.listMarketApplication(
                    projectIds,
                    TypeUtil.cast(searchParamMap.get(TypeUtil.SEARCH_PARAM)),
                    TypeUtil.cast(searchParamMap.get(TypeUtil.PARAM))));
        } else {
            applicationShareDTOPageInfo = PageHelper.startPage(
                    pageRequest.getPage(), pageRequest.getSize(), PageRequestUtil.getOrderBy(pageRequest)).doSelectPageInfo(() -> applicationShareMapper.listMarketApplication(projectIds, null, null));
        }
        return applicationShareDTOPageInfo;
    }

    public ApplicationShareDTO baseQuery(Long projectId, Long shareId) {
        List<Long> projectIds = getProjectIds(projectId);
        return applicationShareMapper.queryByShareId(projectId, shareId, projectIds);
    }

    public Boolean baseCheckPub(Long appId) {

        int selectCount = applicationShareMapper.countByAppId(appId);
        if (selectCount > 0) {
            throw new CommonException("error.app.market.check");
        }
        return true;
    }

    public Long baseQueryShareIdByAppId(Long appId) {
        return applicationShareMapper.baseQueryShareIdByAppId(appId);
    }

    public void baseCheckByProjectId(Long projectId, Long shareId) {
        if (applicationShareMapper.checkByProjectId(projectId, shareId) != 1) {
            throw new CommonException("error.appMarket.project.unmatch");
        }
    }

    public void baseCheckByDeployed(Long projectId, Long shareId, Long versionId, List<Long> projectIds) {
        if (applicationShareMapper.checkByDeployed(projectId, shareId, versionId, projectIds) > 0) {
            throw new CommonException("error.appMarket.instance.deployed");
        }
    }

    public void baseUnsharedApplication(Long shareId) {
        applicationShareMapper.changeApplicationVersions(shareId, null, null);
        applicationShareMapper.deleteByPrimaryKey(shareId);
    }

    public void baseUnsharedApplicationVersion(Long shareId, Long versionId) {
        applicationShareMapper.changeApplicationVersions(shareId, versionId, null);
    }

    public void updateVersion(Long appMarketId, Long versionId, Boolean isPublish) {
        applicationShareMapper.changeApplicationVersions(appMarketId, versionId, isPublish);
    }

    public void baseUpdate(ApplicationShareDTO applicationShareDTO) {
        applicationShareDTO.setObjectVersionNumber(
                applicationShareMapper.selectByPrimaryKey(applicationShareDTO.getId()).getObjectVersionNumber());
        if (applicationShareMapper.updateByPrimaryKeySelective(applicationShareDTO) != 1) {
            throw new CommonException("error.update.share.application");
        }
    }

    public List<ApplicationShareVersionDTO> ListByOptions(Long projectId, Long shareId, Boolean isPublish) {
        List<Long> projectIds = getProjectIds(projectId);
        return applicationShareMapper.listAppVersions(projectIds, shareId, isPublish, null, null);
    }


    public PageInfo<ApplicationShareVersionDTO> pageByOptions(Long projectId, Long shareId, Boolean isPublish,
                                                              PageRequest pageRequest, String params) {
        Sort sort = pageRequest.getSort();
        String sortResult = "";
        if (sort != null) {
            sortResult = Lists.newArrayList(pageRequest.getSort().iterator()).stream()
                    .map(t -> {
                        String property = t.getProperty();
                        if (property.equals("version")) {
                            property = "dav.version";
                        } else if (property.equals("updatedDate")) {
                            property = "dav.last_update_date";
                        } else if (property.equals("creationDate")) {
                            property = "dav.creation_date";
                        }

                        return property + " " + t.getDirection();
                    })
                    .collect(Collectors.joining(","));
        }

        Map<String, Object> searchParam = null;
        String param = null;
        if (!StringUtils.isEmpty(params)) {
            Map<String, Object> searchParamMap = json.deserialize(params, Map.class);
            searchParam = TypeUtil.cast(searchParamMap.get(TypeUtil.SEARCH_PARAM));
            param = TypeUtil.cast(searchParamMap.get(TypeUtil.PARAM));
        }
        Map<String, Object> finalSearchParam = searchParam;
        String finalParam = param;
        List<Long> projectIds = getProjectIds(projectId);
        return PageHelper.startPage(pageRequest.getPage(), pageRequest.getSize(), sortResult).doSelectPageInfo(
                () -> applicationShareMapper.listAppVersions(
                        projectIds, shareId, isPublish,
                        finalSearchParam, finalParam));
    }

    public ApplicationShareDTO baseQueryByAppId(Long appId) {
        ApplicationShareDTO applicationShareDTO = new ApplicationShareDTO();
        applicationShareDTO.setAppId(appId);
        return applicationShareMapper.selectOne(applicationShareDTO);
    }

    public void baseCheckByShareIdAndVersion(Long shareId, Long versionId) {
        if (!applicationShareMapper.checkByShareIdAndVersion(shareId, versionId)) {
            throw new CommonException("error.version.notMatch");
        }
    }

    private List<Long> getProjectIds(Long projectId) {
        List<Long> projectIds;
        if (projectId != null) {
            ProjectDTO projectDTO = iamServiceClientOperator.queryIamProjectById(projectId);
            List<ProjectDTO> projectEList = iamServiceClientOperator.listIamProjectByOrgId(projectDTO.getOrganizationId(), null, null);
            projectIds = projectEList.stream().map(ProjectDTO::getId)
                    .collect(Collectors.toCollection(ArrayList::new));
        } else {
            projectIds = null;
        }
        return projectIds;
    }

    public PageInfo<ApplicationShareDTO> basePageByShareIds(PageRequest pageRequest, String param, List<Long> shareIds) {
        Map<String, Object> mapParams = TypeUtil.castMapParams(param);
        PageInfo<ApplicationShareDTO> applicationShareDTOPageInfo = PageHelper.startPage(pageRequest.getPage(), pageRequest.getSize(), PageRequestUtil.getOrderBy(pageRequest)).doSelectPageInfo(
                () -> applicationShareMapper.queryByShareIds((Map<String, Object>) mapParams.get(TypeUtil.SEARCH_PARAM), (String) mapParams.get(TypeUtil.PARAM), shareIds));
        return applicationShareDTOPageInfo;
    }


    public void baseUpdatePublishLevel() {
        applicationShareMapper.updatePublishLevel();
    }
}
