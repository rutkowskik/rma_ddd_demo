package pl.neopak.rma.returnmanagement.adapter.out.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.neopak.rma.returnmanagement.port.out.PhotoStoragePort;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class LocalDiskPhotoStorageAdapter implements PhotoStoragePort {

    private final Path baseDir;
    private final String baseUrl;

    public LocalDiskPhotoStorageAdapter(
            @Value("${rma.photos.dir:${java.io.tmpdir}/rma-photos}") String dir,
            @Value("${rma.photos.base-url:http://localhost:8080/photos}") String baseUrl) {
        this.baseDir = Paths.get(dir);
        this.baseUrl = baseUrl;
    }

    @Override
    public String store(byte[] photoBytes, String rmaNumber, String filename) {
        try {
            Path dir = baseDir.resolve(rmaNumber);
            Files.createDirectories(dir);
            Path file = dir.resolve(filename);
            Files.write(file, photoBytes);
            return baseUrl + "/" + rmaNumber + "/" + filename;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store photo: " + filename, e);
        }
    }

    @Override
    public void delete(String url) {
        String relative = url.replace(baseUrl + "/", "");
        Path file = baseDir.resolve(relative);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete photo: " + url, e);
        }
    }
}
