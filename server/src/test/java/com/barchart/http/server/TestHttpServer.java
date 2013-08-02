/**
 * Copyright (C) 2011-2013 Barchart, Inc. <http://www.barchart.com/>
 *
 * All rights reserved. Licensed under the OSI BSD License.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package com.barchart.http.server;

import static org.junit.Assert.*;
import io.netty.channel.nio.NioEventLoopGroup;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.auth.DigestScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.barchart.http.auth.Authenticator;
import com.barchart.http.auth.BasicAuthorizationHandler;
import com.barchart.http.auth.DigestAuthorizationHandler;
import com.barchart.util.test.junit.MultiThreadedRunner;

/**
 * MJS: We run it in parallel to stress the server more and also to take
 * advantage of multiple cores for speed
 */
@Ignore
@RunWith(MultiThreadedRunner.class)
public class TestHttpServer {

	private HttpServer server;
	private HttpClient client;

	// MJS: We need separate ports since we run in parallel
	private int port;

	private TestRequestHandler basic;
	private TestRequestHandler async;
	private TestRequestHandler asyncDelayed;
	private TestRequestHandler clientDisconnect;
	private TestRequestHandler error;
	private TestRequestHandler channelError;

	// MJS: To test synctatic precedence for handler referencing
	private TestRequestHandler serviceHandler;
	private TestRequestHandler infoHandler;

	private TestAuthenticator testAuthenticator;

	// MJS: Looks like for Jenkins we need to resort to our own random ports as
	// final ServerSocket s = new ServerSocket(0);.. doesn't seem to work
	static private AtomicInteger ports;

	@BeforeClass
	static public void beforeClass() throws Exception {
		ports = new AtomicInteger(50000);
	}

	@AfterClass
	static public void afterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {

		server = new HttpServer();

		basic = new TestRequestHandler("basic", false, 0, 0, false, false);
		async = new TestRequestHandler("async", true, 0, 0, false, false);
		asyncDelayed = new TestRequestHandler("async-delayed", true, 50, 0,
				false, false);
		clientDisconnect = new TestRequestHandler("", true, 500, 500, false,
				false);
		error = new TestRequestHandler("error", false, 0, 0, true, false);
		channelError = new TestRequestHandler("channel-error", false, 0, 0,
				false, true);

		// MJS: To test synctatic precedence for handler referencing
		infoHandler = new TestRequestHandler("info", false, 0, 0, false, false);
		serviceHandler = new TestRequestHandler("service", false, 0, 0, false,
				false);

		port = ports.getAndIncrement();
		testAuthenticator = new TestAuthenticator();

		final HttpServerConfig config = new HttpServerConfig()
				.requestHandler("/basic", basic)
				.address(new InetSocketAddress("localhost", port))
				.parentGroup(new NioEventLoopGroup(1))
				.childGroup(new NioEventLoopGroup(1))

				// MJS: Request handlers are attached to resources

				.requestHandler("/async", async)
				.requestHandler("/async-delayed", asyncDelayed)
				.requestHandler("/client-disconnect", clientDisconnect)
				.requestHandler("/channel-error", channelError)
				.requestHandler("/error", error)

				// MJS: To test synctatic precedence for handler
				// referencing
				.requestHandler("/service/info", infoHandler)
				.requestHandler("/service", serviceHandler)

				.maxConnections(1);

		server.configure(config).listen().sync();

		client = new DefaultHttpClient(new PoolingClientConnectionManager());

	}

	@After
	public void tearDown() throws Exception {
		if (server.isRunning()) {
			server.shutdown().sync();
		}
	}

	@Test
	public void testBasicRequest() throws Exception {

		for (int i = 0; i < 100; i++) {
			final HttpGet get = new HttpGet("http://localhost:" + port
					+ "/basic");
			final HttpResponse response = client.execute(get);
			final String content = new BufferedReader(new InputStreamReader(
					response.getEntity().getContent())).readLine().trim();

			assertEquals("basic", content);
		}
	}

	// MJS: We test basic authentication by encoding the user:password in
	// plaintext (least secure)
	@Test
	public void testBasicAuthorization() throws Exception {

		// MJS: We add a BASIC authentication requirement
		server.config().authorizationHandler(
				new BasicAuthorizationHandler(testAuthenticator));

		// MJS: Right login/password
		for (int i = 0; i < 10; i++) {
			final HttpGet get = new HttpGet("http://localhost:" + port
					+ "/basic");

			// MJS: We stick the wrong authentication in the header
			get.addHeader(BasicScheme.authenticate(
					new UsernamePasswordCredentials("aaa", "bbb"), "UTF-8",
					false));

			final HttpResponse response = client.execute(get);
			final String content = new BufferedReader(new InputStreamReader(
					response.getEntity().getContent())).readLine().trim();
			assertEquals("basic", content);
			assertEquals(202, response.getStatusLine().getStatusCode());
		}

		// MJS: Wrong login/password so reject
		{
			final HttpGet get = new HttpGet("http://localhost:" + port
					+ "/basic");

			// MJS: We stick the wrong authentication in the header
			get.addHeader(BasicScheme.authenticate(
					new UsernamePasswordCredentials("aaa", "wrongpassword"),
					"UTF-8", false));

			final HttpResponse response = client.execute(get);
			final String content = new BufferedReader(new InputStreamReader(
					response.getEntity().getContent())).readLine();
			assertEquals(401, response.getStatusLine().getStatusCode());
		}

		// MJS: No authentication so automatic reject - We also loop around to
		// see if there are any leaks connected to the reject issue
		for (int i = 0; i < 10; i++) {
			final HttpGet get = new HttpGet("http://localhost:" + port
					+ "/basic");

			// MJS: We stick the wrong authentication in the header
			get.addHeader(BasicScheme.authenticate(
					new UsernamePasswordCredentials("aaa", "wrongpassword"),
					"UTF-8", false));

			final HttpResponse response = client.execute(get);
			final String content = new BufferedReader(new InputStreamReader(
					response.getEntity().getContent())).readLine();
			assertEquals(401, response.getStatusLine().getStatusCode());
		}
	}

	// MJS: Here we test the Digest authentication scheme
	@Test
	public void testDigestAuthentication() throws Exception {

		// MJS: We add a DIGEST authentication requirement
		server.config().authorizationHandler(
				new DigestAuthorizationHandler(testAuthenticator));

		final HttpHost targetHost = new HttpHost("localhost", port, "http");
		final DefaultHttpClient client = new DefaultHttpClient();

		// Create AuthCache instance
		final AuthCache authCache = new BasicAuthCache();

		// Generate DIGEST scheme object, initialize it and add it to the
		// local auth cache
		final DigestScheme digestAuth = new DigestScheme();

		// Suppose we already know the realm name
		digestAuth.overrideParamter("realm", "barchart.com");

		// Suppose we already know the expected nonce value
		digestAuth.overrideParamter("nonce", "whatever");

		authCache.put(targetHost, digestAuth);

		// Add AuthCache to the execution context
		final BasicHttpContext localcontext = new BasicHttpContext();
		localcontext.setAttribute(ClientContext.AUTH_CACHE, authCache);

		final HttpGet httpget = new HttpGet("http://localhost:" + port
				+ "/basic");

		// MJS: Below is how a Digest request is setup and made, more elaborate
		// compared to a Basic one but the security is way better
		{
			final String userName = "aaa";
			final String password = "bbb";

			client.getCredentialsProvider().setCredentials(
					new AuthScope("localhost", port),
					new UsernamePasswordCredentials(userName, password));

			final HttpResponse response = client.execute(targetHost, httpget,
					localcontext);

			final String content = new BufferedReader(new InputStreamReader(
					response.getEntity().getContent())).readLine().trim();
			assertEquals(202, response.getStatusLine().getStatusCode());
			assertEquals("basic", content);
		}
		{
			final String userName = "aaa";
			final String password = "badpassword";

			client.getCredentialsProvider().setCredentials(
					new AuthScope("localhost", port),
					new UsernamePasswordCredentials(userName, password));

			final HttpResponse response = client.execute(targetHost, httpget,
					localcontext);

			final String content = new BufferedReader(new InputStreamReader(
					response.getEntity().getContent())).readLine().trim();
			assertEquals(401, response.getStatusLine().getStatusCode());
		}
	}

	@Test
	public void testAsyncRequest() throws Exception {

		final HttpGet get = new HttpGet("http://localhost:" + port + "/async");
		final HttpResponse response = client.execute(get);
		final String content = new BufferedReader(new InputStreamReader(
				response.getEntity().getContent())).readLine().trim();

		assertNotNull(async.lastFuture);
		assertFalse(async.lastFuture.isCancelled());
		assertEquals("async", content);

	}

	@Test
	public void testAsyncDelayedRequest() throws Exception {

		final HttpGet get = new HttpGet("http://localhost:" + port
				+ "/async-delayed");
		final HttpResponse response = client.execute(get);
		final String content = new BufferedReader(new InputStreamReader(
				response.getEntity().getContent())).readLine().trim();

		assertNotNull(asyncDelayed.lastFuture);
		assertFalse(asyncDelayed.lastFuture.isCancelled());
		assertEquals("async-delayed", content);

	}

	@Test
	public void testUnknownHandler() throws Exception {

		final HttpGet get = new HttpGet("http://localhost:" + port + "/unknown");
		final HttpResponse response = client.execute(get);
		assertEquals(404, response.getStatusLine().getStatusCode());

	}

	@Test
	public void testServerError() throws Exception {

		final HttpGet get = new HttpGet("http://localhost:" + port + "/error");
		final HttpResponse response = client.execute(get);
		assertEquals(500, response.getStatusLine().getStatusCode());

	}

	@Test
	public void testReuseRequest() throws Exception {

		// Parameters were being remembered between requests in pooled objects
		HttpGet get = new HttpGet("http://localhost:" + port
				+ "/basic?field=value");
		HttpResponse response = client.execute(get);
		assertEquals(200, response.getStatusLine().getStatusCode());
		EntityUtils.consume(response.getEntity());

		assertEquals(1, basic.parameters.get("field").size());
		assertEquals("value", basic.parameters.get("field").get(0));

		get = new HttpGet("http://localhost:" + port + "/basic?field=value2");
		response = client.execute(get);
		assertEquals(200, response.getStatusLine().getStatusCode());
		EntityUtils.consume(response.getEntity());

		assertEquals(1, basic.parameters.get("field").size());
		assertEquals("value2", basic.parameters.get("field").get(0));

	}

	@Test
	public void testMultipleRequests() throws Exception {

		// New Beta3 was failing on second request due to shared buffer use
		for (int i = 0; i < 100; i++) {
			final HttpGet get = new HttpGet("http://localhost:" + port
					+ "/basic");
			final HttpResponse response = client.execute(get);
			assertEquals(200, response.getStatusLine().getStatusCode());
			EntityUtils.consume(response.getEntity());
		}

	}

	@Test
	public void testPatternRequests() throws Exception {

		// MJS: how we select handlers based on syntactic priority given to
		// shortest matches is critical and was missing
		{
			final HttpGet get = new HttpGet("http://localhost:" + port
					+ "/service/info/10");
			final HttpResponse response = client.execute(get);
			final String content = new BufferedReader(new InputStreamReader(
					response.getEntity().getContent())).readLine().trim();

			assertEquals("info", content);
		}

		{
			final HttpGet get = new HttpGet("http://localhost:" + port
					+ "/service/something/else");
			final HttpResponse response = client.execute(get);
			final String content = new BufferedReader(new InputStreamReader(
					response.getEntity().getContent())).readLine().trim();

			assertEquals("service", content);
		}
	}

	@Test
	public void testTooManyConnections() throws Exception {

		final Queue<Integer> status = new LinkedBlockingQueue<Integer>();

		final Runnable r = new Runnable() {
			@Override
			public void run() {
				try {
					final HttpResponse response = client.execute(new HttpGet(
							"http://localhost:" + port + "/client-disconnect"));
					status.add(response.getStatusLine().getStatusCode());
				} catch (final Exception e) {
					e.printStackTrace();
				}
			}
		};

		final Thread t1 = new Thread(r);
		t1.start();

		final Thread t2 = new Thread(r);
		t2.start();

		t1.join();
		t2.join();

		assertEquals(2, status.size());
		assertTrue(status.contains(200));
		assertTrue(status.contains(503));

	}

	@Test
	public void testShutdown() throws Exception {

		final ScheduledExecutorService executor = Executors
				.newScheduledThreadPool(1);

		final AtomicBoolean pass = new AtomicBoolean(false);

		executor.schedule(new Runnable() {

			@Override
			public void run() {

				try {
					server.shutdown().sync();
				} catch (final InterruptedException e1) {
					e1.printStackTrace();
				}

				try {
					client.execute(new HttpGet("http://localhost:" + port
							+ "/basic"));
				} catch (final HttpHostConnectException hhce) {
					pass.set(true);
				} catch (final Exception e) {
					e.printStackTrace();
				}

			}

		}, 1000, TimeUnit.MILLISECONDS);

		final HttpGet get = new HttpGet("http://localhost:" + port
				+ "/client-disconnect");
		final HttpResponse response = client.execute(get);
		assertEquals(200, response.getStatusLine().getStatusCode());
		// assertTrue(pass.get());

	}

	@Test(expected = HttpHostConnectException.class)
	public void testKill() throws Exception {

		final ScheduledExecutorService executor = Executors
				.newScheduledThreadPool(1);

		executor.schedule(new Runnable() {

			@Override
			public void run() {
				server.kill();
			}

		}, 500, TimeUnit.MILLISECONDS);

		final HttpGet get = new HttpGet("http://localhost:" + port
				+ "/client-disconnect");

		// Should throw exception
		client.execute(get);

	}

	// @Test
	// Exposed old server handler issue, "Response has already been started"
	public void testRepeated() throws Exception {
		for (int i = 0; i < 10000; i++) {
			testAsyncRequest();
		}
	}

	// MJS: Authenticator we use for BASIC and DIGEST testing
	private class TestAuthenticator implements Authenticator {

		@Override
		public boolean authenticate(final String username, final String password) {
			return username.equals("aaa") && password.equals("bbb");
		}

		@Override
		public String getData(final String username) {

			// MJS: In a real server a DB containing the hashes would be used to
			// shield the real passwords. Only brute force could reveal those
			if (username.equals("aaa"))
				return DigestUtils.md5Hex(username + ":barchart.com:" + "bbb");

			return null;
		}

	}
}
