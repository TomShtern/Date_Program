package datingapp.core.media;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/** Framework-free abstraction for user photo bytes. Implementations own the layout. */
public interface PhotoStore {

    ManagedPhoto store(UUID userId, String contentType, byte[] bytes) throws IOException;

    Optional<PhotoContent> load(UUID userId, String fileName) throws IOException;

    void delete(String storedPath) throws IOException;

    void deleteAllForUser(UUID userId) throws IOException;

    boolean exists(UUID userId, String fileName);

    record ManagedPhoto(String id, String storedPath) {}

    record PhotoContent(byte[] bytes, String contentType) {
        public PhotoContent {
            bytes = bytes == null ? null : bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes == null ? null : bytes.clone();
        }
    }
}
