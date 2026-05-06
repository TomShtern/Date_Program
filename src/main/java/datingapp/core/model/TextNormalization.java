package datingapp.core.model;

import java.io.IOException;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * Shared text normalization utilities for the core domain layer.
 *
 * <p>These methods are stateless and safe to call from any layer.
 * They were extracted from {@code ValidationService} to remove a
 * service-to-model dependency.
 */
public final class TextNormalization {

    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MIN_PHONE_DIGITS = 7;
    private static final int MAX_PHONE_DIGITS = 15;
    private static final Pattern EMAIL_LOCAL_PATTERN = Pattern.compile("^[^\\s@]+$");
    private static final Pattern DOMAIN_LABEL_PATTERN =
            Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$");
    private static final Pattern PHONE_ALLOWED_PATTERN = Pattern.compile("^[+0-9()\\-\\s]+$");
    private static final Set<String> PHOTO_URL_SCHEMES = Set.of("http", "https");
    private static final String FILE_URLS_ENABLED_ENV = "DATING_APP_ALLOW_FILE_URLS";
    private static final String FILE_URLS_ENABLED_PROPERTY = "datingapp.allowFileUrls";
    private static final String FILE_URL_ROOT_ENV = "DATING_APP_ALLOWED_FILE_URL_ROOT";
    private static final String FILE_URL_ROOT_PROPERTY = "datingapp.allowedFileUrlRoot";
    private static final long DNS_LOOKUP_TIMEOUT_MILLIS = 2_000L;
    private static final ExecutorService DNS_LOOKUP_EXECUTOR =
            Executors.newCachedThreadPool(new DnsLookupThreadFactory());

    private TextNormalization() {
        // Utility class
    }

    public static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String normalized = Normalizer.normalize(email.trim(), Normalizer.Form.NFKC);
        if (normalized.length() > MAX_EMAIL_LENGTH) {
            throw new IllegalArgumentException("Invalid email format");
        }
        int atIndex = normalized.lastIndexOf('@');
        if (atIndex <= 0 || atIndex == normalized.length() - 1 || normalized.indexOf('@') != atIndex) {
            throw new IllegalArgumentException("Invalid email format");
        }

        String localPart = normalized.substring(0, atIndex);
        String domainPart = normalized.substring(atIndex + 1);
        if (!EMAIL_LOCAL_PATTERN.matcher(localPart).matches()
                || containsControlCharacters(localPart)
                || domainPart.isBlank()) {
            throw new IllegalArgumentException("Invalid email format");
        }

        String asciiDomain;
        try {
            asciiDomain = IDN.toASCII(domainPart);
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("Invalid email format");
        }
        if (!isValidAsciiDomain(asciiDomain)) {
            throw new IllegalArgumentException("Invalid email format");
        }

        return localPart + "@" + asciiDomain.toLowerCase(Locale.ROOT);
    }

    public static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String normalized = phone.trim();
        if (!PHONE_ALLOWED_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid phone format");
        }
        long plusCount = normalized.chars().filter(ch -> ch == '+').count();
        if (plusCount > 1 || (plusCount == 1 && !normalized.startsWith("+"))) {
            throw new IllegalArgumentException("Invalid phone format");
        }
        String digitsOnly = normalized.replaceAll("\\D", "");
        if (digitsOnly.length() < MIN_PHONE_DIGITS || digitsOnly.length() > MAX_PHONE_DIGITS) {
            throw new IllegalArgumentException("Invalid phone format");
        }
        return normalized.startsWith("+") ? "+" + digitsOnly : digitsOnly;
    }

    public static String normalizePhotoUrl(String photoUrl) {
        if (photoUrl == null || photoUrl.isBlank()) {
            return null;
        }

        URI uri;
        try {
            uri = new URI(photoUrl.trim()).normalize();
        } catch (URISyntaxException _) {
            throw new IllegalArgumentException("Invalid photo URL");
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("Invalid photo URL");
        }

        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        if ("file".equals(normalizedScheme)) {
            return normalizeFilePhotoUrl(uri);
        }
        if (!PHOTO_URL_SCHEMES.contains(normalizedScheme)) {
            throw new IllegalArgumentException("Invalid photo URL");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("Invalid photo URL");
        }
        if (isUnsafeHost(uri.getHost())) {
            throw new IllegalArgumentException("Invalid photo URL");
        }

        return uri.toASCIIString();
    }

    private static String normalizeFilePhotoUrl(URI uri) {
        if (!isFilePhotoUrlEnabled()) {
            throw new IllegalArgumentException("Invalid photo URL");
        }
        if (uri.getAuthority() != null && !uri.getAuthority().isBlank()) {
            throw new IllegalArgumentException("Invalid photo URL");
        }
        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Invalid photo URL");
        }

        try {
            Path normalizedPath = Path.of(uri).toAbsolutePath().toRealPath();
            Path allowedRoot = resolveAllowedFileUrlRoot().toRealPath();
            if (!normalizedPath.startsWith(allowedRoot)) {
                throw new IllegalArgumentException("Invalid photo URL");
            }

            return normalizedPath.toUri().toASCIIString();
        } catch (IOException | RuntimeException _) {
            throw new IllegalArgumentException("Invalid photo URL");
        }
    }

    private static boolean isFilePhotoUrlEnabled() {
        String configured =
                firstNonBlank(System.getProperty(FILE_URLS_ENABLED_PROPERTY), System.getenv(FILE_URLS_ENABLED_ENV));
        return "true".equalsIgnoreCase(configured);
    }

    private static Path resolveAllowedFileUrlRoot() {
        String configured = firstNonBlank(System.getProperty(FILE_URL_ROOT_PROPERTY), System.getenv(FILE_URL_ROOT_ENV));
        try {
            if (configured != null) {
                return Path.of(configured).toAbsolutePath();
            }
            return Path.of(System.getProperty("user.home"), ".datingapp", "photos")
                    .toAbsolutePath();
        } catch (RuntimeException _) {
            throw new IllegalArgumentException("Invalid photo URL");
        }
    }

    public static boolean isUnsafeHost(String host) {
        if (host == null || host.isBlank()) {
            return true;
        }
        try {
            InetAddress[] addresses = resolveAddresses(host);
            if (addresses.length == 0) {
                return true;
            }
            for (InetAddress address : addresses) {
                if (isUnsafeAddress(address)) {
                    return true;
                }
            }
            return false;
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return true;
        } catch (java.net.UnknownHostException | TimeoutException | RuntimeException _) {
            return true;
        }
    }

    private static InetAddress[] resolveAddresses(String host)
            throws java.net.UnknownHostException, InterruptedException, TimeoutException {
        Future<InetAddress[]> lookup = DNS_LOOKUP_EXECUTOR.submit(() -> InetAddress.getAllByName(host));
        try {
            return lookup.get(DNS_LOOKUP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (ExecutionException ex) {
            return rethrowLookupFailure(ex.getCause());
        } catch (TimeoutException ex) {
            lookup.cancel(true);
            throw ex;
        }
    }

    private static InetAddress[] rethrowLookupFailure(Throwable cause) throws java.net.UnknownHostException {
        if (cause instanceof java.net.UnknownHostException unknownHostException) {
            throw unknownHostException;
        }
        if (cause instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new IllegalStateException("Host lookup failed", cause);
    }

    private static boolean isUnsafeAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isPrivateIpv4(address)
                || isUniqueLocalIpv6(address);
    }

    private static boolean isPrivateIpv4(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        byte[] octets = address.getAddress();
        int first = Byte.toUnsignedInt(octets[0]);
        int second = Byte.toUnsignedInt(octets[1]);
        return first == 10
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || (first == 169 && second == 254);
    }

    private static boolean isUniqueLocalIpv6(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        byte[] bytes = address.getAddress();
        return (Byte.toUnsignedInt(bytes[0]) & 0xFE) == 0xFC;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private static boolean containsControlCharacters(String value) {
        return value.chars().anyMatch(ch -> Character.isISOControl(ch) || Character.isWhitespace(ch));
    }

    private static boolean isValidAsciiDomain(String domain) {
        if (domain == null || domain.isBlank() || domain.length() > 253) {
            return false;
        }
        String[] labels = domain.split("\\.");
        if (labels.length < 2) {
            return false;
        }
        for (String label : labels) {
            if (label.isBlank()
                    || label.length() > 63
                    || !DOMAIN_LABEL_PATTERN.matcher(label).matches()) {
                return false;
            }
        }
        return true;
    }

    private static final class DnsLookupThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            return Thread.ofPlatform()
                    .name("text-normalization-dns", 0)
                    .daemon()
                    .unstarted(runnable);
        }
    }
}
