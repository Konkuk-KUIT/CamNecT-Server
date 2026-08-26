package CamNecT.server.global.common.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentWorkflowContractTest {

    @Test
    void stageAndProductionDeploymentsRunTestsBeforeBuildingAnImage() throws Exception {
        assertTestGatePrecedesImageBuild(".github/workflows/deploy-stage.yml");
        assertTestGatePrecedesImageBuild(".github/workflows/deploy.yml");
    }

    private void assertTestGatePrecedesImageBuild(String workflowPath) throws Exception {
        String workflow = Files.readString(Path.of(workflowPath));
        int testGate = workflow.indexOf("./gradlew test --no-daemon");
        int imageBuild = workflow.indexOf("docker/build-push-action");

        assertThat(testGate)
                .as("%s must run the test suite", workflowPath)
                .isGreaterThanOrEqualTo(0);
        assertThat(imageBuild)
                .as("%s must build the deployment image", workflowPath)
                .isGreaterThan(testGate);
    }
}
