/**
 * Copyright (C) 2011-2013 Barchart, Inc. <http://www.barchart.com/>
 *
 * All rights reserved. Licensed under the OSI BSD License.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package com.barchart.http.request;

import aQute.bnd.annotation.ConsumerType;

/**
 * Factory for creating RequestHandler instances, which allows more control over
 * request handler lifecycle. Using a RequestHandlerFactory in conjunction with
 * RequestHandler.onComplete() can allow for construction of a cached handler
 * pool while still allowing request-specific state to be stored in the handler.
 */
@ConsumerType
public interface RequestHandlerFactory {

	/**
	 * Create a new request handler. Subclasses can use this method in
	 * conjunction with onComplete() to implement limited-size request handler
	 * pools to avoid excessive object creation and garbage collection.
	 */
	RequestHandler newHandler(ServerRequest request);

}
