package co.infinum.princeofversions.network;

import android.util.Base64;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import co.infinum.princeofversions.LoaderValidationException;
import co.infinum.princeofversions.UpdateConfigLoader;
import co.infinum.princeofversions.exceptions.UrlNotSetException;
import co.infinum.princeofversions.helpers.StreamIO;

/**
 * Represents a concrete loader that load resource from network using provided URL.
 */
public class NetworkLoader implements UpdateConfigLoader {

    /**
     * Default request timeout in seconds.
     */
    public static final int DEFAULT_NETWORK_TIMEOUT_SECONDS = 60;

    /**
     * Url representing the resource.
     */
    private String url;

    /**
     * Custom network timeout in seconds.
     */
    private int networkTimeoutSeconds;

    /**
     * Basic authentication username.
     */
    private String username;

    /**
     * Basic authentication password.
     */
    private String password;

    /**
     * Cancellation flag.
     */
    private volatile boolean cancelled = false;

    /**
     * Creates a new network loader using provided url.
     * @param url Resource locator.
     */
    public NetworkLoader(String url) {
        this(url, DEFAULT_NETWORK_TIMEOUT_SECONDS);
    }

    /**
     * Creates a new network loader using url and custom network timeout.
     * @param url Resource locator.
     * @param networkTimeoutSeconds Custom network timeout.
     */
    public NetworkLoader(String url, int networkTimeoutSeconds) {
        this(url, null, null, networkTimeoutSeconds);
    }

    /**
     * Creates a new network loader using url, default network timeout and basic authentication parameters.
     * @param url Resource locator.
     * @param username Basic authentication username.
     * @param password Basic authentication password.
     */
    public NetworkLoader(String url, String username, String password) {
        this(url, username, password, DEFAULT_NETWORK_TIMEOUT_SECONDS);
    }

    /**
     * Creates a new network loader using url, custom network timeout and basic authentication parameters.
     * @param url Resource locator.
     * @param username Basic authentication username.
     * @param password Basic authentication password.
     * @param networkTimeoutSeconds Custom network timeout.
     */
    public NetworkLoader(String url, String username, String password, int networkTimeoutSeconds) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.networkTimeoutSeconds = networkTimeoutSeconds;
    }

    @Override
    public String load() throws IOException, InterruptedException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            if (username != null && password != null) {
                String credentials = username + ":" + password;
                String basicAuth = "Basic " + Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
                conn.setRequestProperty ("Authorization", basicAuth);
            }
            conn.setConnectTimeout(networkTimeoutSeconds * 1000);
            InputStream response = conn.getInputStream();
            ifTaskIsCancelledThrowInterrupt(); // if cancelled here no need to read stream at all
            String content = StreamIO.toString(response, new StreamIO.StreamLineFilter() {
                @Override
                public Command apply(String line) {
                    if (cancelled) {
                        // if cancelled here no need to read anymore
                        return Command.STOP;
                    } else {
                        return Command.GO;
                    }
                }
            });
            return content;
        } finally {
            close(conn);
        }
    }

    /**
     * Checks if loading is cancelled and throwing interrupt if it is.
     * @throws InterruptedException if loading is cancelled.
     */
    private void ifTaskIsCancelledThrowInterrupt() throws InterruptedException {
        if (cancelled) {
            throw new InterruptedException();
        }
    }

    @Override
    public void cancel() {
        this.cancelled = true;
    }

    @Override
    public void validate() throws LoaderValidationException {
        if (url == null) {
            throw new UrlNotSetException("Url is not set.");
        }
    }

    /**
     * Closing http connection.
     * @param conn Http connection.
     */
    private void close(HttpURLConnection conn) {
        if (conn != null) {
            try {
                conn.disconnect();
            } catch (Exception ignorable) {}
        }
    }
}
