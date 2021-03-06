package io.choerodon.devops.api.ws;

import static io.choerodon.devops.infra.constant.DevOpsWebSocketConstants.*;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import io.choerodon.devops.api.vo.PipeRequestVO;
import io.choerodon.devops.app.service.AgentCommandService;

/**
 * Created by Sheep on 2019/7/25.
 */
@Component
public class DevopsExecAndLogSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(DevopsExecAndLogSocketHandler.class);

    @Autowired
    private AgentCommandService agentCommandService;


    public boolean beforeHandshake(ServerHttpRequest serverHttpRequest, ServerHttpResponse serverHttpResponse) {

        ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) serverHttpRequest;
        HttpServletRequest request = servletRequest.getServletRequest();

        //校验ws连接参数是否正确
        WebSocketTool.checkKey(request);
        WebSocketTool.checkGroup(request);
        WebSocketTool.checkEnv(request);
        WebSocketTool.checkPodName(request);
        WebSocketTool.checkContainerName(request);
        WebSocketTool.checkLogId(request);
        WebSocketTool.checkClusterId(request);

        return true;
    }

    public void afterConnectionEstablished(WebSocketSession webSocketSession) {
        //解析参数列表
        Map<String, Object> attribute = webSocketSession.getAttributes();

        String frontSessionGroup = WebSocketTool.getGroup(webSocketSession);
        String key = WebSocketTool.getKey(webSocketSession);
        String processor = WebSocketTool.getProcessor(webSocketSession);

        logger.info("Connection established from client. The sessionGroup is {} and the processor is {}", frontSessionGroup, processor);

        // 通过GitOps的ws连接，通知agent建立与前端对应的ws连接
        PipeRequestVO pipeRequest = new PipeRequestVO(
                attribute.get(POD_NAME).toString(),
                attribute.get(CONTAINER_NAME).toString(),
                attribute.get(LOG_ID).toString(),
                attribute.get(ENV).toString());

        Long clusterId = WebSocketTool.getClusterId(webSocketSession);

        if (FRONT_LOG.equals(processor)) {
            agentCommandService.startLogOrExecConnection(KUBERNETES_GET_LOGS, key, pipeRequest, clusterId);
        } else {
            agentCommandService.startLogOrExecConnection(EXEC_COMMAND, key, pipeRequest, clusterId);
        }
    }

    public void afterConnectionClosed(WebSocketSession webSocketSession, CloseStatus closeStatus) {
        // 关闭agent那边的web socket Session
        WebSocketTool.closeAgentSessionByKey(WebSocketTool.getKey(webSocketSession));
        WebSocketTool.closeSessionQuietly(webSocketSession);
    }
}
