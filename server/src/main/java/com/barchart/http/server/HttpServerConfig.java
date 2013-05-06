/**
 * Copyright (C) 2011-2013 Barchart, Inc. <http://www.barchart.com/>
 *
 * All rights reserved. Licensed under the OSI BSD License.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package com.barchart.http.server;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import java.net.SocketAddress;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import com.barchart.http.auth.AuthorizationHandler;
import com.barchart.http.error.DefaultErrorHandler;
import com.barchart.http.error.ErrorHandler;
import com.barchart.http.logging.NullRequestLogger;
import com.barchart.http.logging.RequestLogger;
import com.barchart.http.request.RequestHandler;
import com.barchart.http.request.RequestHandlerFactory;
import com.barchart.http.request.RequestHandlerMapping;

/**
 * Configuration values for initializing HttpServer.
 */
public class HttpServerConfig {

	private final Map<String, AuthorizationHandler> authorizationHandlers =
			new ConcurrentHashMap<String, AuthorizationHandler>();

	private final Map<String, Object> handlers =
			new ConcurrentSkipListMap<String, Object>(
					new ReverseLengthComparator());

	private SocketAddress address;
	private int maxConnections = -1;
	private int maxRequestSize = 65536;
	private ErrorHandler errorHandler = new DefaultErrorHandler();
	private RequestLogger requestLogger = new NullRequestLogger();
	private EventLoopGroup parentGroup = new NioEventLoopGroup();
	private EventLoopGroup childGroup = new NioEventLoopGroup();

	/**
	 * Set the server listen address.
	 */
	public HttpServerConfig address(final SocketAddress address_) {
		address = address_;
		return this;
	}

	/**
	 * Set the maximum number of client connections.
	 */
	public HttpServerConfig maxConnections(final int max) {
		maxConnections = max;
		return this;
	}

	/**
	 * Set the maximum request size in bytes (file uploads, etc). Defaults to
	 * 65536.
	 */
	public HttpServerConfig maxRequestSize(final int max) {
		maxRequestSize = max;
		return this;
	}

	/**
	 * Set the default error handler.
	 */
	public HttpServerConfig errorHandler(final ErrorHandler handler) {
		errorHandler = handler;
		return this;
	}

	/**
	 * Set the request logger.
	 */
	public HttpServerConfig logger(final RequestLogger logger_) {
		requestLogger = logger_;
		return this;
	}

	/**
	 * Set the parent (listen port) event loop group.
	 */
	public HttpServerConfig parentGroup(final EventLoopGroup group) {
		parentGroup = group;
		return this;
	}

	/**
	 * Set the child (request handler) event loop group.
	 */
	public HttpServerConfig childGroup(final EventLoopGroup group) {
		childGroup = group;
		return this;
	}

	/**
	 * Add a request handler for the given path.
	 */
	public HttpServerConfig requestHandler(final String path,
			final RequestHandler handler) {
		handlers.put(path, handler);
		return this;
	}

	/**
	 * Add a request handler factory for the given path.
	 */
	public HttpServerConfig requestHandler(final String path,
			final RequestHandlerFactory factory) {
		handlers.put(path, factory);
		return this;
	}

	/**
	 * Add an authorization handler.
	 */
	public HttpServerConfig authorizationHandler(
			final AuthorizationHandler authHandler) {
		authorizationHandlers.put(authHandler.getMethod().toUpperCase(),
				authHandler);
		return this;
	}

	/**
	 * Get the address this server binds to.
	 */
	public SocketAddress address() {
		return address;
	}

	/**
	 * Get the maximum number of client connections.
	 */
	public int maxConnections() {
		return maxConnections;
	}

	/**
	 * Get the maximum request size in bytes.
	 */
	public int maxRequestSize() {
		return maxRequestSize;
	}

	/**
	 * Get the default error handler.
	 */
	public ErrorHandler errorHandler() {
		return errorHandler;
	}

	/**
	 * Get the request logger.
	 */
	public RequestLogger logger() {
		return requestLogger;
	}

	/**
	 * Get the parent Netty event loop group.
	 */
	public EventLoopGroup parentGroup() {
		return parentGroup;
	}

	/**
	 * Get the child Netty event loop group.
	 */
	public EventLoopGroup childGroup() {
		return childGroup;
	}

	/**
	 * Get the request handler mapping for the specified URI.
	 */
	public RequestHandlerMapping getRequestMapping(final String uri) {

		for (final Map.Entry<String, Object> entry : handlers.entrySet()) {
			if (uri.startsWith(entry.getKey())) {
				return RequestHandlerMapping.create(entry.getKey(),
						entry.getValue());
			}
		}

		return null;

	}

	public Object removeRequestHandler(final String path) {
		return handlers.remove(path);
	}

	/**
	 * Indicates if we need to authenticate upon requests
	 */
	public boolean hasAuthorizationHandlers() {
		return authorizationHandlers.size() > 0;
	}

	/**
	 * Get the authorization handler for the specified header.
	 */
	public AuthorizationHandler getAuthorizationHandler(final String header) {
		return authorizationHandlers.get(header.split(" ")[0].toUpperCase());
	}

	/**
	 * Sorts strings by reverse length first, then normal comparison.
	 */
	private class ReverseLengthComparator implements Comparator<String> {

		// Sort by reverse length first to allow overriding parent
		// mappings
		@Override
		public int compare(final String o1, final String o2) {

			final int l1 = o1.length();
			final int l2 = o2.length();

			if (l1 < l2) {
				return 1;
			} else if (l2 < l1) {
				return -1;
			} else {
				return o1.compareTo(o2);
			}

		}

	}

}
