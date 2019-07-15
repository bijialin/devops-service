package io.choerodon.devops.infra.persistence.impl;

import java.util.List;
import java.util.Map;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.gson.Gson;
import io.choerodon.base.domain.PageRequest;
import io.choerodon.devops.infra.util.PageRequestUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.choerodon.core.convertor.ConvertHelper;
import io.choerodon.core.convertor.ConvertPageHelper;
import io.choerodon.core.exception.CommonException;
import io.choerodon.devops.api.vo.iam.entity.PipelineRecordE;
import io.choerodon.devops.domain.application.repository.PipelineRecordRepository;
import io.choerodon.devops.infra.util.TypeUtil;
import io.choerodon.devops.infra.dto.PipelineRecordDTO;
import io.choerodon.devops.infra.mapper.PipelineRecordMapper;

/**
 * Creator: ChangpingShi0213@gmail.com
 * Date:  16:39 2019/4/4
 * Description:
 */
@Component
public class PipelineRecordRepositoryImpl implements PipelineRecordRepository {
    private static final Gson gson = new Gson();

    @Autowired
    private PipelineRecordMapper pipelineRecordMapper;

    @Override
    public PageInfo<PipelineRecordE> basePageByOptions(Long projectId, Long pipelineId, PageRequest pageRequest, String params, Map<String, Object> classifyParam) {
        Map maps = gson.fromJson(params, Map.class);
        Map<String, Object> searchParamMap = TypeUtil.cast(maps.get(TypeUtil.SEARCH_PARAM));
        String paramMap = TypeUtil.cast(maps.get(TypeUtil.PARAM));
        PageInfo<PipelineRecordDTO> pipelineDOS = PageHelper.startPage(pageRequest.getPage(),pageRequest.getSize(), PageRequestUtil.getOrderBy(pageRequest)).doSelectPageInfo(() ->
                pipelineRecordMapper.listByOptions(projectId, pipelineId, searchParamMap, paramMap, classifyParam));
        return ConvertPageHelper.convertPageInfo(pipelineDOS, PipelineRecordE.class);
    }

    @Override
    public PipelineRecordE baseCreate(PipelineRecordE pipelineRecordE) {
        PipelineRecordDTO pipelineRecordDO = ConvertHelper.convert(pipelineRecordE, PipelineRecordDTO.class);
        if (pipelineRecordMapper.insert(pipelineRecordDO) != 1) {
            throw new CommonException("error.insert.pipeline.record");
        }
        return ConvertHelper.convert(pipelineRecordDO, PipelineRecordE.class);
    }

    @Override
    public PipelineRecordE baseUpdate(PipelineRecordE pipelineRecordE) {
        PipelineRecordDTO pipelineRecordDO = ConvertHelper.convert(pipelineRecordE, PipelineRecordDTO.class);
        pipelineRecordDO.setObjectVersionNumber(pipelineRecordMapper.selectByPrimaryKey(pipelineRecordDO).getObjectVersionNumber());
        if (pipelineRecordMapper.updateByPrimaryKeySelective(pipelineRecordDO) != 1) {
            throw new CommonException("error.update.pipeline.record");
        }
        return ConvertHelper.convert(pipelineRecordDO, PipelineRecordE.class);
    }

    @Override
    public PipelineRecordE baseQueryById(Long recordId) {
        return ConvertHelper.convert(pipelineRecordMapper.selectByPrimaryKey(recordId), PipelineRecordE.class);
    }

    @Override
    public List<PipelineRecordE> baseQueryByPipelineId(Long pipelineId) {
        PipelineRecordDTO pipelineRecordDO = new PipelineRecordDTO();
        pipelineRecordDO.setPipelineId(pipelineId);
        return ConvertHelper.convertList(pipelineRecordMapper.select(pipelineRecordDO), PipelineRecordE.class);
    }

    @Override
    public void baseUpdateWithEdited(Long pipelineId) {
        pipelineRecordMapper.updateEdited(pipelineId);
    }

    @Override
    public List<Long> baseQueryAllRecordUserIds(Long pipelineRecordId) {
        return pipelineRecordMapper.queryAllRecordUserIds(pipelineRecordId);
    }

}
