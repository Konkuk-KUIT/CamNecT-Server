package CamNecT.server.domain.gifticon.service;

public record GifticonExportMailResult(boolean successful, String error) {

    public static GifticonExportMailResult delivered() {
        return new GifticonExportMailResult(true, null);
    }

    public static GifticonExportMailResult failed(String error) {
        return new GifticonExportMailResult(false, error);
    }
}
