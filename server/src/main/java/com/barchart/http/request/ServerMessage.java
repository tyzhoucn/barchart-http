/**
 * Copyright (C) 2011-2013 Barchart, Inc. <http://www.barchart.com/>
 *
 * All rights reserved. Licensed under the OSI BSD License.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package com.barchart.http.request;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpVersion;
import aQute.bnd.annotation.ProviderType;

/**
 * Information about an inbound request.
 */
@ProviderType
public interface ServerMessage extends HttpMessage {

	@Override
	public HttpHeaders headers();

	@Override
	public HttpVersion getProtocolVersion();

}
