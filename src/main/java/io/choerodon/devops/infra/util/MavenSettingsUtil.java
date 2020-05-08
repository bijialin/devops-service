package io.choerodon.devops.infra.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.xml.sax.helpers.DefaultHandler;

import io.choerodon.core.exception.CommonException;
import io.choerodon.devops.infra.dto.maven.*;

/**
 * 用于生成Maven的Settings文件的工具类
 *
 * @author zmf
 * @since 20-4-14
 */
public class MavenSettingsUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenSettingsUtil.class);

    private MavenSettingsUtil() {
    }

    private static final String DEFAULT_PROFILE_ID = "default";

    /**
     * 数组字节流的初始大小
     */
    private static final int BYTE_ARRAY_INIT_SIZE = 3000;

    /**
     * 生成maven的settings文件
     *
     * @param servers      用户认证信息
     * @param repositories 仓库信息
     * @return 生成的settings文件
     */
    public static String generateMavenSettings(List<Server> servers, List<Repository> repositories) {
        try {
            // 获取JAXB的上下文环境，需要传入具体的 Java bean -> 这里使用Settings
            JAXBContext context = JAXBContext.newInstance(Settings.class);

            // 创建 Marshaller 实例
            Marshaller marshaller = context.createMarshaller();

            // 设置转换参数 -> 序列化器是否格式化输出
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

            // 将所需对象序列化到字节数组流中
            ByteArrayOutputStream out = new ByteArrayOutputStream(BYTE_ARRAY_INIT_SIZE);
            marshaller.marshal(initSettings(servers, repositories), out);

            // 将字节流转为字节数组再转到字符串
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (JAXBException e) {
            LOGGER.warn("Maven util: internal errors: failed to generate settings: servers: {}, repositories: {}", servers, repositories);
            throw new CommonException("error.generate.maven.settings");
        }
    }

    /**
     * 校验字符串内容是否符合xml文件
     *
     * @param xmlFormatContent xml格式的字符串内容
     * @return true表示是, false表示不是
     */
    public static boolean isXmlFormat(String xmlFormatContent) {
        Assert.hasLength(xmlFormatContent, "Xml content to be judged can not be null or empty");
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser parser = factory.newSAXParser();
            DefaultHandler handler = new DefaultHandler();
            parser.parse(new ByteArrayInputStream(xmlFormatContent.getBytes(StandardCharsets.UTF_8)), handler);
        } catch (Exception e) {
            // 如果解析出错，则不是xml格式的内容
            return false;
        }
        return true;
    }

    private static Settings initSettings(List<Server> servers, List<Repository> repositories) {
        return new Settings(servers, initProfiles(repositories));
    }

    private static List<Profile> initProfiles(List<Repository> repositories) {
        return ArrayUtil.singleAsList(new Profile(DEFAULT_PROFILE_ID, new Activation(true), repositories));
    }
}
