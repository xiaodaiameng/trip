package com.softbei.scenicai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StartupReporter implements ApplicationRunner {

    @Value("${server.port}")
    private int serverPort;

    @Override
    public void run(ApplicationArguments args) {
        log.info("灵山胜境 AI 导览服务启动完成，访问地址：http://localhost:{}", serverPort);
        log.info("H2 控制台已就绪，访问地址：http://localhost:{}/h2-console", serverPort);
    }
}
