/*******************************************************************************
 * Copyright (c) 2011 Google, Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * 	   Sergey Prigogin (Google) - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.core.index;

import java.util.Map;

/**
 * A key that uniquely determines the preprocessed content of a file. 
 * @since 5.4
 */
public interface IFileContentKey {
	/**
	 * Returns an IIndexFileLocation representing the location of the file.
	 */
	IIndexFileLocation getLocation();

	/**
	 * Returns the names and definitions of the macros that affect the preprocessed contents of
	 * the file or the files it includes directly or indirectly. Undefined macros have
	 * <code>null</code> values in the map. May return <code>null</code> if the relevant macros are
	 * unknown.
	 */
	Map<String, String> getRelevantMacros();

	/**
	 * Returns the name of the macro that, if defined, makes the preprocessed contents of the file
	 * not contain any code irrespectively of other macros. Returns <code>null</code> if the file
	 * does not have the include guard macro.
	 */
	String getIncludeGuardMacro();
}