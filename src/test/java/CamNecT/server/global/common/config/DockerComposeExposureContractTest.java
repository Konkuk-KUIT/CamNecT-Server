package CamNecT.server.global.common.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DockerComposeExposureContractTest {

    @Test
    void localDatastoresArePublishedOnlyOnLoopback() throws Exception {
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(compose).contains(
                "127.0.0.1:3307:3306",
                "127.0.0.1:6379:6379"
        );
        assertThat(compose).doesNotContain(
                "- \"3307:3306\"",
                "- \"6379:6379\""
        );
    }
}
