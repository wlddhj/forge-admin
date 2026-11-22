package com.forge.modules.workflow.service.bizview;

import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.forge.common.exception.BusinessException;
import com.forge.modules.system.dto.attachment.AttachmentResponse;
import com.forge.modules.system.service.SysAttachmentService;
import com.forge.modules.workflow.dto.bizview.WfBizViewData;
import com.forge.modules.workflow.dto.bizview.WfBizViewResponse;
import com.forge.modules.workflow.framework.instance.WfInstanceAccessService;
import com.forge.modules.workflow.spi.WfBizViewProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务单据视图组装服务（formType=20 业务表单的透出通道）。
 * <p>
 * businessKey 按约定 {bizType}:{bizId} 解析后路由到 {@link WfBizViewProvider}，
 * 统一走「实例参与人」鉴权；provider 缺失/单据不存在/实现异常一律降级
 * available=false，由前端回退实例变量快照，不阻断审批界面。
 *
 * @author forge-admin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfBizViewService {

    private final List<WfBizViewProvider> providers;
    private final QueryService queryService;
    private final WfInstanceAccessService accessService;
    private final SysAttachmentService sysAttachmentService;

    private final Map<String, WfBizViewProvider> providerMap = new HashMap<>();

    @PostConstruct
    void initProviderMap() {
        for (WfBizViewProvider provider : providers) {
            WfBizViewProvider previous = providerMap.put(provider.bizType(), provider);
            if (previous != null) {
                throw new IllegalStateException("业务单据视图 SPI 的 bizType 重复: " + provider.bizType()
                        + "（" + previous.getClass().getName() + " 与 " + provider.getClass().getName() + "）");
            }
        }
    }

    /**
     * 获取实例业务单据只读视图
     */
    public WfBizViewResponse getBizView(String instanceIdStr, Long userId) {
        Long instanceId = requireParticipant(instanceIdStr, userId);
        BizKey bizKey = resolveBizKey(instanceId);
        if (bizKey == null) {
            return unavailable();
        }
        WfBizViewProvider provider = providerMap.get(bizKey.bizType());
        if (provider == null) {
            return unavailable();
        }
        WfBizViewData data;
        try {
            data = provider.getBizView(bizKey.bizId());
        } catch (Exception e) {
            log.warn("业务单据视图 SPI 异常，降级为快照兜底: bizType={}, bizId={}", bizKey.bizType(), bizKey.bizId(), e);
            return unavailable();
        }
        if (data == null) {
            return unavailable();
        }

        WfBizViewResponse response = new WfBizViewResponse();
        response.setAvailable(true);
        response.setBizType(bizKey.bizType());
        response.setBizId(bizKey.bizId());
        response.setTitle(data.getTitle());
        response.setFields(data.getFields() == null ? List.of() : data.getFields());
        response.setPreviewEnabled(data.isPreviewEnabled());
        response.setPreviewContentType(provider.previewContentType());
        response.setAttachments(loadAttachments(data));
        return response;
    }

    /**
     * 获取单据预览字节流（如合同 PDF）
     *
     * @throws BusinessException 404 该流程无业务单据视图或不支持预览
     */
    public BizPreviewPayload getBizPreview(String instanceIdStr, Long userId) {
        Long instanceId = requireParticipant(instanceIdStr, userId);
        BizKey bizKey = resolveBizKey(instanceId);
        WfBizViewProvider provider = bizKey == null ? null : providerMap.get(bizKey.bizType());
        if (provider == null) {
            throw new BusinessException(404, "该流程无业务单据视图");
        }
        byte[] content = provider.getBizPreview(bizKey.bizId());
        if (content == null || content.length == 0) {
            throw new BusinessException(404, "该单据不支持预览");
        }
        return new BizPreviewPayload(content, provider.previewContentType());
    }

    /**
     * 下载业务单据附件（附件必须属于该实例单据的 attachmentRef 域，防跨实例探测）
     *
     * @throws BusinessException 404 附件不属于该单据或不存在
     */
    public AttachmentPayload downloadAttachment(String instanceIdStr, Long attachmentId, Long userId) {
        Long instanceId = requireParticipant(instanceIdStr, userId);
        BizKey bizKey = resolveBizKey(instanceId);
        WfBizViewProvider provider = bizKey == null ? null : providerMap.get(bizKey.bizType());
        if (provider == null) {
            throw new BusinessException(404, "该流程无业务单据视图");
        }
        WfBizViewData data;
        try {
            data = provider.getBizView(bizKey.bizId());
        } catch (Exception e) {
            log.warn("业务单据视图 SPI 异常: bizType={}, bizId={}", bizKey.bizType(), bizKey.bizId(), e);
            throw new BusinessException(404, "该流程无业务单据视图");
        }
        if (data == null || data.getAttachmentRef() == null) {
            throw new BusinessException(404, "该单据无附件");
        }
        AttachmentResponse target = sysAttachmentService.listByBiz(
                        data.getAttachmentRef().getBizType(), data.getAttachmentRef().getBizId()).stream()
                .filter(a -> attachmentId.equals(a.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "附件不存在或不属于该单据"));
        return new AttachmentPayload(sysAttachmentService.openStream(attachmentId, userId).stream(),
                target.getOriginalName(), target.getFileType());
    }

    /**
     * 参与人校验统一入口：解析实例 ID 并校验当前用户参与人身份
     */
    private Long requireParticipant(String instanceIdStr, Long userId) {
        Long instanceId;
        try {
            instanceId = Long.parseLong(instanceIdStr);
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "流程实例ID格式错误");
        }
        if (userId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }
        if (!accessService.isParticipant(instanceId, userId)) {
            throw new BusinessException(403, "无权查看该流程的业务单据");
        }
        return instanceId;
    }

    /**
     * 解析实例 businessKey；实例不存在抛 404，businessKey 缺失/格式不合法返回 null（降级）
     */
    private BizKey resolveBizKey(Long instanceId) {
        FlwInstance instance = queryService.getInstance(instanceId);
        String businessKey;
        if (instance != null) {
            businessKey = instance.getBusinessKey();
        } else {
            FlwHisInstance hisInstance = queryService.getHistInstance(instanceId);
            if (hisInstance == null) {
                throw new BusinessException(404, "流程实例不存在");
            }
            businessKey = hisInstance.getBusinessKey();
        }
        if (businessKey == null || businessKey.isBlank()) {
            return null;
        }
        int separatorIndex = businessKey.indexOf(':');
        if (separatorIndex <= 0 || separatorIndex == businessKey.length() - 1) {
            log.warn("businessKey 不符合 {{bizType}}:{{bizId}} 约定: {}", businessKey);
            return null;
        }
        return new BizKey(businessKey.substring(0, separatorIndex), businessKey.substring(separatorIndex + 1));
    }

    private List<WfBizViewResponse.AttachmentItem> loadAttachments(WfBizViewData data) {
        if (data.getAttachmentRef() == null) {
            return List.of();
        }
        return sysAttachmentService.listByBiz(
                        data.getAttachmentRef().getBizType(), data.getAttachmentRef().getBizId()).stream()
                .map(a -> {
                    WfBizViewResponse.AttachmentItem item = new WfBizViewResponse.AttachmentItem();
                    item.setId(a.getId());
                    item.setName(a.getOriginalName());
                    item.setSize(a.getFileSize());
                    item.setExtension(a.getFileExtension());
                    return item;
                })
                .toList();
    }

    private WfBizViewResponse unavailable() {
        WfBizViewResponse response = new WfBizViewResponse();
        response.setAvailable(false);
        return response;
    }

    /** businessKey 解析结果（{bizType}:{bizId}） */
    private record BizKey(String bizType, String bizId) {
    }

    /** 单据预览载荷 */
    public record BizPreviewPayload(byte[] content, String contentType) {
    }

    /** 附件下载载荷（流式：由调用方响应链消费并关闭，打开阶段已验证可读） */
    public record AttachmentPayload(InputStream content, String fileName, String contentType) {
    }
}
