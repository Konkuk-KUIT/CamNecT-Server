package CamNecT.server.domain.report.model.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.report.evidence")
public record ReportEvidenceProps(
        long maxFileSizeMb
) {
    public long maxFileSizeBytes() { return maxFileSizeMb * 1024L * 1024L; }
}
