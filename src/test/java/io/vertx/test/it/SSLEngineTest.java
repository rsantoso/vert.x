package io.vertx.test.it;

import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.OpenSslContext;
import io.netty.handler.ssl.SslContext;
import io.vertx.core.VertxException;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.HttpServerImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.net.impl.SSLHelper;
import io.vertx.test.core.HttpTestBase;
import io.vertx.test.core.TLSCert;
import org.junit.Test;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class SSLEngineTest extends HttpTestBase {

  private static final boolean JDK = Boolean.getBoolean("vertx-test-alpn-jdk");
  private static boolean OPEN_SSL = Boolean.getBoolean("vertx-test-alpn-openssl");
  private static final String EXPECTED_SSL_CONTEXT = System.getProperty("vertx-test-sslcontext");

  public SSLEngineTest() {
  }

  @Test
  public void testDefaultEngineWithAlpn() throws Exception {
    doTest(null, true, HttpVersion.HTTP_2, JDK | OPEN_SSL ? "ALPN is not available" : null, EXPECTED_SSL_CONTEXT);
  }

  @Test
  public void testJdkEngineWithAlpn() throws Exception {
    doTest(new JdkSSLEngineOptions(), true, HttpVersion.HTTP_2, JDK ? "ALPN not available for JDK SSL/TLS engine" : null, "jdk");
  }

  @Test
  public void testOpenSSLEngineWithAlpn() throws Exception {
    doTest(new OpenSSLEngineOptions(), true, HttpVersion.HTTP_2, OPEN_SSL ? "OpenSSL is not available" : null, "openssl");
  }

  @Test
  public void testDefaultEngine() throws Exception {
    doTest(null, false, HttpVersion.HTTP_1_1, null, "jdk");
  }

  @Test
  public void testJdkEngine() throws Exception {
    doTest(new JdkSSLEngineOptions(), false, HttpVersion.HTTP_1_1, null, "jdk");
  }

  @Test
  public void testOpenSSLEngine() throws Exception {
    doTest(new OpenSSLEngineOptions(), false, HttpVersion.HTTP_1_1, "OpenSSL is not available", "openssl");
  }

  private void doTest(SSLEngineOptions engine,
                      boolean useAlpn, HttpVersion version, String error, String expectedSslContext) {
    server.close();
    HttpServerOptions options = new HttpServerOptions()
        .setSslEngineOptions(engine)
        .setPort(DEFAULT_HTTP_PORT)
        .setHost(DEFAULT_HTTP_HOST)
        .setKeyCertOptions(TLSCert.PEM.getServerKeyCertOptions())
        .setSsl(true)
        .setUseAlpn(useAlpn);
    try {
      server = vertx.createHttpServer(options);
    } catch (VertxException e) {
      e.printStackTrace();
      if (error == null) {
        fail(e);
      } else {
        assertEquals(error, e.getMessage());
      }
      return;
    }
    server.requestHandler(req -> {
      assertEquals(req.version(), version);
      assertTrue(req.isSSL());
      req.response().end();
    });
    server.listen(onSuccess(s -> {
      HttpServerImpl impl = (HttpServerImpl) s;
      SSLHelper sslHelper = impl.getSslHelper();
      SslContext ctx = sslHelper.getContext((VertxInternal) vertx);
      switch (expectedSslContext) {
        case "jdk":
          assertTrue(ctx instanceof JdkSslContext);
          break;
        case "openssl":
          assertTrue(ctx instanceof OpenSslContext);
          break;
      }
      client = vertx.createHttpClient(new HttpClientOptions()
          .setSslEngineOptions(engine)
          .setSsl(true)
          .setUseAlpn(useAlpn)
          .setTrustAll(true)
          .setProtocolVersion(version));
      client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
        assertEquals(200, resp.statusCode());
        testComplete();
      });
    }));
    await();
  }
}
