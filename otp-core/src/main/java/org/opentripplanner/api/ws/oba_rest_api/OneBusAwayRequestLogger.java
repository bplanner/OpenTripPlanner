/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.api.ws.oba_rest_api;

import com.brsanthu.googleanalytics.ExceptionHit;
import com.brsanthu.googleanalytics.GoogleAnalytics;
import com.brsanthu.googleanalytics.GoogleAnalyticsConfig;
import com.brsanthu.googleanalytics.GoogleAnalyticsRequest;
import com.brsanthu.googleanalytics.GoogleAnalyticsResponse;
import com.brsanthu.googleanalytics.PageViewHit;
import com.brsanthu.googleanalytics.TimingHit;
import com.sun.jersey.api.core.HttpContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.TextUtils;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponse;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponseBuilder;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class OneBusAwayRequestLogger {

    private static String EMPTY = "(empty)";
    private static int counter = 1;
    private static int QUEUE_LIMIT = 5000;
    private static int THREADS = 8;

    private static GoogleAnalytics ga = new OBAGoogleAnalytics("UA-50283889-5");
    private static final int TIMEOUT_CONNECTION = 1000;
    private static final int TIMEOUT_SOCKET = 1000;
    private static final String HOSTNAME = getHostname();

    public static boolean isEnabled = false;

    public OneBusAwayRequestLogger() {
    }

    public LogRequest startRequest(Object apiMethod, HttpContext req, URI uri, String clientId, String apiKey, String appVersion, boolean internalRequest, TransitResponseBuilder.DialectWrapper dialect) {
        if (clientId == null) {
            clientId = "" + ++counter;
        }

        return new LogRequest(apiMethod, req, uri, clientId, apiKey, appVersion, internalRequest, dialect.getDialect().name());
    }

    private static String getHostname() {
        try {
            String result = InetAddress.getLocalHost().getHostName();
            if (StringUtils.isNotEmpty(result)) {
                return result;
            }
        } catch (UnknownHostException e) {
        }

        String host = System.getenv("COMPUTERNAME");
        if (host != null)
            return host;

        host = System.getenv("HOSTNAME");
        if (host != null)
            return host;

        return null;
    }

    public class LogRequest {

        private final long startTime = System.currentTimeMillis();
        private final PageViewHit pageHit = new PageViewHit();
        private final TimingHit timingHit = new TimingHit();

        private Object apiMethod;
        private String dialect;
        private String userLanguage;
        private String userAgent;
        private String userIp;
        private String clientId;
        private String url;
        private String apiKey;
        private String appVersion;
        private boolean internalRequest;

        protected LogRequest(Object apiMethod, HttpContext req, URI uri, String clientId, String apiKey, String appVersion, boolean internalRequest, String dialect) {

            this.clientId = clientId;
            this.apiMethod = apiMethod;

            this.apiKey = apiKey;
            this.appVersion = appVersion;
            this.internalRequest = internalRequest;
            this.dialect = dialect;

            this.url = uri.toString();
            this.userLanguage = req.getRequest().getHeaderValue("Accept-Language");
            this.userAgent = req.getRequest().getHeaderValue("User-Agent");
            this.userIp = req.getRequest().getHeaderValue("X-Forwarded-For");
            if (this.userIp == null) {
                this.userIp = ""; // req.getRemoteAddr();
            }

            init(pageHit);
            pageHit.documentTitle(this.apiMethod.getClass().getSimpleName());

            init(timingHit);
            timingHit.userTimingLabel(this.url);
            timingHit.userTimingCategory("apiRequest");
            timingHit.userTimingVariableName(this.apiMethod.getClass().getSimpleName());
        }

        private <T extends GoogleAnalyticsRequest<T>> T init(T gar) {
            gar.userAgent(userAgent);
            gar.userIp(userIp);
            gar.userLanguage(userLanguage);
            gar.customMetric(1, "" + (internalRequest ? 1 : 0));
            gar.customDimention(1, TextUtils.isEmpty(apiKey) ? EMPTY : apiKey);
            gar.customDimention(2, apiMethod.getClass().getSimpleName());
            gar.customDimention(3, dialect);
            gar.customDimention(6, HOSTNAME);
            gar.customDimention(7, TextUtils.isEmpty(appVersion) ? EMPTY : appVersion);
            gar.clientId(clientId);
            gar.documentUrl(url);

            return gar;
        }

        public void finishRequest(TransitResponse<?> response) {
            long now = System.currentTimeMillis();
            //timingHit.pageLoadTime(10000 + (int) (now - startTime));
            //timingHit.serverResponseTime(5000 + (int) (now - startTime));
            timingHit.userTimingTime((int) (now - startTime));
            if (isEnabled)
                ga.postAsync(timingHit);

            pageHit.customDimention(4, response.getText());
            pageHit.customDimention(5, Integer.toString(response.getCode()));

            if (isEnabled)
                ga.postAsync(pageHit);
        }

        public void exception(TransitResponse<?> transitResponse, Exception e) {
            ExceptionHit exceptionHit = init(new ExceptionHit());
            exceptionHit.exceptionFatal(true);
            exceptionHit.exceptionDescription(e.getMessage());
            if (isEnabled)
                ga.postAsync(exceptionHit);

            pageHit.customDimention(4, transitResponse.getText());
            pageHit.customDimention(5, Integer.toString(transitResponse.getCode()));

            if (isEnabled)
                ga.postAsync(pageHit);
        }
    }

    @Slf4j
    private static class OBAGoogleAnalytics extends GoogleAnalytics {

        private Random random = new Random();

        public OBAGoogleAnalytics(String trackingId) {
            super(createConfig(), trackingId);

            PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
            connManager.setDefaultMaxPerRoute(getDefaultMaxPerRoute(getConfig()));
            new IdleConnectionMonitorThread(connManager).start();

            HttpClientBuilder builder = HttpClients.custom().setConnectionManager(connManager);
            builder.setUserAgent(getConfig().getUserAgent());
            builder.setDefaultRequestConfig(
                    RequestConfig
                            .custom()
                            .setConnectionRequestTimeout(TIMEOUT_CONNECTION)
                            .setConnectTimeout(TIMEOUT_SOCKET)
                            .setSocketTimeout(TIMEOUT_SOCKET)
                            .build()
            );

            setHttpClient(builder.build());
        }

        @Override
        public Future<GoogleAnalyticsResponse> postAsync(GoogleAnalyticsRequest request) {
            if(random.nextInt(100) < 1) {
                try {
                    return super.postAsync(request);
                } catch (RejectedExecutionException exception) {
                    log.warn("Rejected google analytics request: {}", exception.getMessage());
                    return null;
                }
            }
            return null;
        }

        @Override
        protected synchronized ThreadPoolExecutor createExecutor(GoogleAnalyticsConfig config) {
            return new ThreadPoolExecutor(0, config.getMaxThreads(), 5, TimeUnit.MINUTES, new ArrayBlockingQueue<Runnable>(QUEUE_LIMIT), createThreadFactory());
        }

        private static GoogleAnalyticsConfig createConfig() {
            GoogleAnalyticsConfig config = new GoogleAnalyticsConfig();
            config.setDiscoverRequestParameters(false);
            config.setUserAgent("OTP-API");
            config.setMaxThreads(THREADS);
            config.setGatherStats(true);
            config.setUseHttps(false);
            return config;
        }
    }

    private static class IdleConnectionMonitorThread extends Thread {
        private final HttpClientConnectionManager connectionManager;
        private volatile boolean shutdown;

        private IdleConnectionMonitorThread(HttpClientConnectionManager connectionManager) {
            super("Google Analytics idle connection cleanup");
            this.connectionManager = connectionManager;
        }

        @Override
        public void run() {
            try {
                while (!shutdown) {
                    synchronized (this) {
                        wait(5000);

                        // Close expired connections
                        connectionManager.closeExpiredConnections();
                        // Optionally, close connections
                        // that have been idle longer than 30 sec
                        connectionManager.closeIdleConnections(30, TimeUnit.SECONDS);
                    }
                }
            } catch (InterruptedException ex) {
                // terminate
            }
        }

        public void shutdown() {
            shutdown = true;
            synchronized (this) {
                notifyAll();
            }
        }
    }
}
